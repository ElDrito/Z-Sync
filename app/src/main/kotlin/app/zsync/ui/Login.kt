package app.zsync.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zsync.LoginStep
import app.zsync.MainVm
import app.zsync.core.Auth
import org.json.JSONObject

@Composable
fun LoginScreen(vm: MainVm, onDone: () -> Unit, onBack: () -> Unit) {
    val step by vm.loginStep.collectAsStateWithLifecycle()
    val error by vm.loginError.collectAsStateWithLifecycle()
    val keepStoken = vm.repo.settings.collectAsStateWithLifecycle().value.keepStoken
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    LaunchedEffect(step) { if (step is LoginStep.Done) onDone() }

    Page("Log in", onBack, backdrop = false) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            when (val st = step) {
                is LoginStep.EmailCode -> {
                    Text(if (st.sent) "HoYoVerse sent you a verification code by email. Enter it to authorize this device."
                        else "HoYoVerse asks for an email verification code to authorize this device.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(code, { code = it.filter(Char::isLetterOrDigit).take(8) }, label = { Text("Code") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
                    ErrorText(error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.submitEmailCode(st, code) }, enabled = code.length >= 4, modifier = Modifier.fillMaxWidth()) { Text("Verify") }
                    TextButton(onClick = { vm.sendCode(st) }) { Text("Resend code") }
                    TextButton(onClick = { vm.resetLogin() }) { Text("Start over") }
                }
                else -> {
                    val working = st is LoginStep.Working || st is LoginStep.Captcha
                    Text(
                        "Use your HoYoVerse account (the same as HoYoLAB). The password is encrypted and sent only to HoYoVerse; it is not stored. " +
                            if (keepStoken) "This method gets the stoken, which lets the cookies renew automatically."
                            else "Automatic renewal is turned off, so the stoken will not be saved.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(email, { email = it }, label = { Text("Email or username") }, singleLine = true, enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, enabled = !working,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    if (!manual) ErrorText(error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { manual = false; vm.login(email, password) }, enabled = !working && email.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Log in")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("If your account signs in with Google, Apple or similar, first create a password at account.hoyoverse.com or use the option below.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { manual = !manual }, modifier = Modifier.offset(x = (-12).dp)) { Text(if (manual) "Hide" else "Paste a cookie manually") }
                    if (manual) {
                        Text("Paste the full cookie (ltoken_v2, ltuid_v2, ltmid_v2; optionally cookie_token_v2, account_id_v2, account_mid_v2 and stoken).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(cookie, { cookie = it }, label = { Text("Cookie") }, minLines = 3, maxLines = 6, enabled = !working, modifier = Modifier.fillMaxWidth())
                        ErrorText(error)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { vm.loginWithCookie(cookie) }, enabled = !working && cookie.contains("ltoken_v2")) { Text("Use this cookie") }
                    }
                }
            }
        }
    }

    (step as? LoginStep.Captcha)?.let { st ->
        CaptchaDialog(st.captcha, onSolved = { vm.captchaSolved(st, it) }, onCancel = { vm.captchaCancelled(st, it) })
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaDialog(captcha: Auth.Captcha, onSolved: (String) -> Unit, onCancel: (String?) -> Unit) {
    Dialog(onDismissRequest = { onCancel(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(12.dp), shape = MaterialTheme.shapes.large, color = Color.White) {
            Column {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Verification", Modifier.weight(1f), color = Color.Black)
                    TextButton(onClick = { onCancel(null) }) { Text("Cancel") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(object {
                                @JavascriptInterface fun done(json: String) { post { onSolved(json) } }
                                @JavascriptInterface fun fail(msg: String) { post { onCancel("Captcha: $msg") } }
                            }, "Android")
                            loadDataWithBaseURL("https://account.hoyolab.com/", captchaHtml(captcha), "text/html", "utf-8", null)
                        }
                    },
                )
            }
        }
    }
}

private fun captchaHtml(c: Auth.Captcha): String {
    val mmt = JSONObject().apply {
        put("gt", c.gt); put("challenge", c.challenge ?: ""); put("risk_type", c.riskType ?: "")
        put("session_id", c.sessionId); put("new_captcha", c.newCaptcha)
    }
    val script = if (c.v4) "https://static.geetest.com/v4/gt4.js" else "https://static.geetest.com/static/js/gt.0.5.0.js"
    return """
<!DOCTYPE html><html><head><meta name="referrer" content="no-referrer"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<style>body{font-family:sans-serif;color:#444;text-align:center;padding-top:40px}</style></head>
<body><p id="s">Loading verification…</p>
<script src="$script" onerror="Android.fail('could not load Geetest')"></script>
<script>
const v4 = ${c.v4};
const mmt = $mmt;
const init = v4 ? window.initGeetest4 : window.initGeetest;
const params = v4 ? {
  captchaId: mmt.gt, riskType: mmt.risk_type,
  userInfo: JSON.stringify({session_id: mmt.session_id}),
  apiServers: ["gcaptcha4.captchami.com"], product: "bind", language: "en"
} : {
  gt: mmt.gt, challenge: mmt.challenge, new_captcha: mmt.new_captcha,
  api_server: "api-na.geetest.com", https: true, product: "bind", lang: "en"
};
try {
  init(params, function (captcha) {
    captcha.onReady(function () { document.getElementById('s').textContent = ''; v4 ? captcha.showCaptcha() : captcha.verify(); });
    captcha.onSuccess(function () { Android.done(JSON.stringify(captcha.getValidate())); });
    captcha.onError(function (e) { Android.fail((e && (e.msg || e.error_code)) || 'error'); });
    if (captcha.onClose) captcha.onClose(function () { Android.fail('closed'); });
  });
} catch (e) { Android.fail(String(e)); }
</script></body></html>"""
}
