package app.wallglass.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.wallglass.auth.AuthApi
import kotlinx.coroutines.launch

private val SETUP_BG = Brush.linearGradient(listOf(Color(0xFF0E1A3A), Color(0xFF1D3470), Color(0xFF2F5FB0)))

/** First run: enter HA address, then log in with HA's own login page. */
@Composable
fun SetupScreen(auth: AuthApi, initialUrl: String?, onLoggedIn: () -> Unit) {
    var url by remember { mutableStateOf(initialUrl ?: "http://homeassistant.local:8123") }
    var loginUrl by remember { mutableStateOf<String?>(null) }
    var loginState by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(SETUP_BG)) {
        val target = loginUrl
        if (target != null) {
            LoginWebView(target, loginState) { code ->
                loginUrl = null
                scope.launch {
                    try {
                        auth.exchangeCode(normalize(url), code)
                        onLoggedIn()
                    } catch (e: Exception) {
                        error = e.message ?: "Logowanie nie powiodło się"
                    }
                }
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Txt("Witaj", 26, FontWeight.Medium)
            Txt("Podaj adres swojego Home Assistanta. Zalogujesz się na następnym ekranie.", 15, color = Color(0xFFDBE6FB))
            BasicTextField(
                value = url,
                onValueChange = { url = it.trim() },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().height(56.dp).glass(14.dp, Color(0x40000000)).padding(16.dp),
            )
            error?.let { Txt(it, 14, color = Color(0xFFFFB4A8)) }
            Box(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.width(160.dp).height(54.dp).glass(14.dp, Color.White, Color.White).tap {
                        error = null
                        loginState = auth.newState()
                        loginUrl = auth.authorizeUrl(normalize(url), loginState)
                    },
                    contentAlignment = Alignment.Center,
                ) { Txt("Dalej", 16, FontWeight.SemiBold, color = Color(0xFF1D3470)) }
            }
        }
    }
}

private fun normalize(raw: String): String {
    val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
    return withScheme.trimEnd('/')
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(url: String, expectedState: String, onCode: (String) -> Unit) {
    val redirect = Uri.parse(AuthApi.REDIRECT_URI)
    var delivered = false
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri: Uri = request.url
                        val isRedirect = uri.scheme == redirect.scheme && uri.host == redirect.host && uri.path == redirect.path
                        if (!isRedirect) return false
                        val code = uri.getQueryParameter("code")
                        if (!delivered && code != null && uri.getQueryParameter("state") == expectedState) {
                            delivered = true
                            onCode(code)
                        }
                        return true
                    }
                }
                loadUrl(url)
            }
        },
    )
}
