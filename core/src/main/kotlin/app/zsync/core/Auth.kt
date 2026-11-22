package app.zsync.core

import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

object Auth {
    private const val LOGIN_URL = "https://sg-public-api.hoyoverse.com/account/ma-passport/api/appLoginByPassword"
    private const val TOKEN_URL = "https://sg-public-api.hoyoverse.com/account/ma-passport/token/getBySToken"
    private const val SEND_CODE_URL = "https://sg-public-api.hoyoverse.com/account/ma-verifier/api/createEmailCaptchaByActionTicket"
    private const val VERIFY_URL = "https://sg-public-api.hoyoverse.com/account/ma-verifier/api/verifyActionTicketPartly"

    private const val RSA_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4PMS2JVMwBsOIrYWRluY" +
            "wEiFZL7Aphtm9z5Eu/anzJ09nB00uhW+ScrDWFECPwpQto/GlOJYCUwVM/raQpAj" +
            "/xvcjK5tNVzzK94mhk+j9RiQ+aWHaTXmOgurhxSp3YbwlRDvOgcq5yPiTz0+kSeK" +
            "ZJcGeJ95bvJ+hJ/UMP0Zx2qB5PElZmiKvfiNqVUk8A8oxLJdBB5eCpqWV6CUqDKQ" +
            "KSQP4sM0mZvQ1Sr4UcACVcYgYnCbTZMWhJTWkrNXqI8TMomekgny3y+d6NX/cFa6" +
            "6jozFIF4HCX5aW8bp8C8vq2tFvFbleQ/Q3CU56EWWKMrOcpmFtRmC18s9biZBVR/" +
            "8QIDAQAB"

    data class Captcha(val sessionId: String, val gt: String, val challenge: String?, val riskType: String?, val v4: Boolean, val newCaptcha: Int)

    sealed class LoginResult {
        data class Success(val cookies: Map<String, String>) : LoginResult()
        data class NeedCaptcha(val captcha: Captcha) : LoginResult()
        data class NeedEmailCode(val verifyHeader: String, val ticket: String) : LoginResult()
        data class Error(val message: String) : LoginResult()
    }

    private fun withCode(res: Resp) = res.reason() + (res.retcode?.let { " (code $it)" } ?: "")

    private fun nested(e: JsonElement?): JsonObject? = when (e) {
        is JsonObject -> e
        is JsonPrimitive -> Json.parseToJsonElement(e.content).jsonObject
        else -> null
    }

    fun newDeviceId() = Http.randomString(16, "abcdefghijklmnopqrstuvwxyz0123456789")

    fun encrypt(text: String): String {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(RSA_KEY)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray()))
    }

    fun aigisHeader(sessionId: String, validateJson: String): String =
        sessionId + ";" + Base64.getEncoder().encodeToString(validateJson.toByteArray())

    fun parseCaptcha(headerValue: String): Captcha? = runCatching {
        val aigis = Json.parseToJsonElement(headerValue).jsonObject
        val data = nested(aigis["data"]) ?: return null
        Captcha(
            sessionId = aigis.str("session_id").orEmpty(),
            gt = data.str("gt").orEmpty(),
            challenge = data.str("challenge"),
            riskType = data.str("risk_type"),
            v4 = data.bool("use_v4") ?: (data.int("use_v4") == 1),
            newCaptcha = data.int("new_captcha") ?: 1,
        )
    }.getOrNull()

    fun login(
        account: String,
        password: String,
        deviceId: String,
        aigis: String? = null,
        verifyHeader: String? = null,
        deviceName: String = "Android",
        deviceModel: String = "Android",
    ): LoginResult {
        val body = buildJsonObject {
            put("account", encrypt(account))
            put("password", encrypt(password))
        }.toString()
        val headers = buildMap {
            put("x-rpc-app_id", Http.APP_ID)
            put("x-rpc-client_type", "2")
            put("x-rpc-aigis_v4", "true")
            put("x-rpc-app_version", "4.8.0")
            put("x-rpc-sdk_version", "2.2.0")
            put("x-rpc-device_id", deviceId)
            put("x-rpc-device_name", deviceName)
            put("x-rpc-device_model", deviceModel)
            aigis?.let { put("x-rpc-aigis", it) }
            verifyHeader?.let { put("x-rpc-verify", it) }
            put("ds", Http.dsAppLogin(body))
        }
        val res = Http.request("POST", LOGIN_URL, headers = headers, body = body)
        if (res.status == -1) return LoginResult.Error("No connection")
        if (res.retcode == -3101) {
            val c = res.headers["x-rpc-aigis"]?.let(::parseCaptcha) ?: return LoginResult.Error("Could not read the captcha")
            return LoginResult.NeedCaptcha(c)
        }
        if (res.retcode == -3239 || (res.data == null && res.headers["x-rpc-verify"] != null)) {
            val raw = res.headers["x-rpc-verify"] ?: return LoginResult.Error("Missing the verification ticket (code ${res.retcode})")
            val ticket = runCatching { nested(Json.parseToJsonElement(raw).jsonObject["verify_str"])?.str("ticket") }.getOrNull()
                ?: return LoginResult.Error("Invalid verification ticket")
            return LoginResult.NeedEmailCode(raw, ticket)
        }
        val data = res.data ?: return LoginResult.Error(withCode(res))
        val stoken = data.obj("token")?.str("token")
        val info = data.obj("user_info")
        val aid = info?.str("aid")
        val mid = info?.str("mid")
        if (stoken.isNullOrEmpty() || aid.isNullOrEmpty() || mid.isNullOrEmpty()) return LoginResult.Error("Incomplete login response")

        val (tokens, error) = tokensFromStoken(stoken, mid)
        if (error != null) return LoginResult.Error(error)
        return LoginResult.Success(
            linkedMapOf(
                "stoken" to stoken,
                "ltuid_v2" to aid,
                "ltmid_v2" to mid,
                "account_id_v2" to aid,
                "account_mid_v2" to mid,
            ) + tokens,
        )
    }

    fun tokensFromStoken(stoken: String, mid: String): Pair<Map<String, String>, String?> {
        val res = Http.request(
            "POST", TOKEN_URL,
            headers = Http.labHeaders("en-us") + mapOf(
                "ds" to Http.ds(Http.APP_LOGIN_SALT),
                "x-rpc-app_id" to Http.APP_ID,
                "Cookie" to "stoken=$stoken; mid=$mid",
            ),
            body = """{"dst_token_types":[2,4]}""",
        )
        if (!res.ok) return emptyMap<String, String>() to res.reason()
        val out = HashMap<String, String>()
        for (t in res.data?.arr("tokens").orEmpty()) {
            when (t.int("token_type")) {
                2 -> t.str("token")?.let { out["ltoken_v2"] = it }
                4 -> t.str("token")?.let { out["cookie_token_v2"] = it }
            }
        }
        if (out.size < 2) return emptyMap<String, String>() to "Tokens missing from the response"
        return out to null
    }

    sealed class SendResult {
        data object Sent : SendResult()
        data class NeedCaptcha(val captcha: Captcha) : SendResult()
        data class Error(val message: String) : SendResult()
    }

    fun sendEmailCode(ticket: String, aigis: String? = null): SendResult {
        val res = Http.request(
            "POST", SEND_CODE_URL,
            headers = buildMap {
                put("x-rpc-app_id", Http.APP_ID); put("x-rpc-client_type", "2")
                aigis?.let { put("x-rpc-aigis", it) }
            },
            body = buildJsonObject { put("action_type", "verify_for_component"); put("action_ticket", ticket) }.toString(),
        )
        if (res.ok) return SendResult.Sent
        if (res.retcode == -3101) res.headers["x-rpc-aigis"]?.let(::parseCaptcha)?.let { return SendResult.NeedCaptcha(it) }
        return SendResult.Error(withCode(res))
    }

    fun verifyEmailCode(ticket: String, code: String): String? {
        val res = Http.request(
            "POST", VERIFY_URL,
            headers = mapOf("x-rpc-app_id" to Http.APP_ID, "x-rpc-client_type" to "2"),
            body = buildJsonObject {
                put("action_type", "verify_for_component")
                put("action_ticket", ticket)
                put("email_captcha", code)
                put("verify_method", 2)
            }.toString(),
        )
        return if (res.ok) null else withCode(res)
    }
}
