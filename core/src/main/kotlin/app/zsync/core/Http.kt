package app.zsync.core

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

const val API_LANG = "en-us"

class Resp(val status: Int, val body: JsonObject?, val headers: Map<String, String>) {
    val retcode: Int? get() = body?.get("retcode")?.jsonPrimitive?.intOrNull
    val message: String get() = body?.get("message")?.jsonPrimitive?.contentOrNull ?: ""
    val data: JsonObject? get() = body?.get("data") as? JsonObject
    val ok get() = status == 200 && retcode == 0

    fun reason(): String = when {
        status == -1 -> "No connection"
        status != 200 -> "HTTP $status"
        else -> Errors.describe(retcode, message)
    }
}

object Errors {
    private fun known(retcode: Int?): String? = when (retcode) {
        -100, -10001, 10001, -1071 -> "The session expired or the cookie is not valid"
        -10101 -> "Daily request limit reached"
        -1048 -> "The server is busy, try again later"
        -2001 -> "The code has expired"
        -2003, -2004 -> "The code is not valid"
        -2006 -> "The code has reached its usage limit"
        -2016 -> "Redemption on cooldown"
        -2017, -2018 -> "The code was already redeemed"
        -2021 -> "Adventure level too low to redeem"
        -5003 -> "You already checked in today"
        1009 -> "The account does not exist"
        10102 -> "The profile data is not public"
        -3101 -> "A captcha is required"
        -3208 -> "Wrong account or password"
        -3203 -> "The account does not exist"
        -3202 -> "Too many attempts, try again later"
        -3206 -> "Incorrect verification code"
        else -> null
    }

    fun describe(retcode: Int?, message: String): String =
        known(retcode) ?: message.ifBlank { "retcode $retcode" }

    private val loginText = Regex("(?:please\\s+)?log\\s*in|login", RegexOption.IGNORE_CASE)

    fun isExpiredLogin(retcode: Int?, message: String?): Boolean =
        retcode == -100 || retcode == -10001 || retcode == 10001 || retcode == -1071 ||
            loginText.containsMatchIn(message ?: "")
}

object Http {
    const val UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    const val DS_SALT = "6s25p5ox5y14umn1p61aqyyvbvvl3lrt"
    const val APP_LOGIN_SALT = "IZPgfb0dRPtBeLuFkdDznSZ6f4wWt6y2"
    const val APP_ID = "c9oqaq3s3gu8"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val rnd = SecureRandom()

    fun labHeaders(language: String) = mapOf(
        "Referer" to "https://act.hoyolab.com",
        "x-rpc-app_version" to "1.5.0",
        "x-rpc-client_type" to "5",
        "x-rpc-language" to language,
        "User-Agent" to UA_WEB,
    )

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun randomString(n: Int, chars: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789") =
        (1..n).map { chars[rnd.nextInt(chars.length)] }.joinToString("")

    // Dynamic secret ("DS") header that HoYoLAB requires: md5 of salt, time and random string.
    fun ds(salt: String = DS_SALT, tail: String = ""): String {
        val t = System.currentTimeMillis() / 1000
        val r = randomString(6)
        return "$t,$r,${md5("salt=$salt&t=$t&r=$r$tail")}"
    }

    fun dsAppLogin(body: String) = ds(APP_LOGIN_SALT, "&b=$body&q=")

    fun request(
        method: String,
        url: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): Resp {
        return try {
            val u = url.toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val rb = when {
                body != null -> body.toRequestBody("application/json".toMediaType())
                method == "POST" -> "".toRequestBody(null)
                else -> null
            }
            val req = Request.Builder().url(u).method(method, rb).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                Resp(res.code, obj, res.headers.names().associate { it.lowercase() to res.headers[it].orEmpty() })
            }
        } catch (e: Exception) {
            Resp(-1, null, emptyMap())
        }
    }
}

fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }
fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
