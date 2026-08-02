package com.maktas.ytconverter.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

// Cover art below this size (either dimension) is rejected outright — never offered as usable.
private const val MIN_COVER_DIMENSION_PX = 640

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CoverSearchScreen(
    query: String,
    onImageCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val isPageLoading = remember { mutableStateOf(true) }
    val isCapturing = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val pendingBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Populated by the JS bridge the instant the user taps a result thumbnail — Bing
    // embeds each thumbnail's true full-resolution source URL as page data, so there's
    // no need to guess from network traffic the way Google Images requires.
    val selectedUrlChannel = remember { Channel<String>(Channel.CONFLATED) }

    pendingBitmap.value?.let { bmp ->
        pendingBitmap.value = null
        onImageCaptured(bmp)
    }

    // Download the tapped image, enforce a minimum resolution, then hand it to the crop screen.
    LaunchedEffect(Unit) {
        for (url in selectedUrlChannel) {
            isCapturing.value = true
            errorMessage.value = null
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 12_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("User-Agent", CHROME_UA)
                    conn.setRequestProperty("Referer", "https://www.bing.com/")
                    conn.connect()
                    if (conn.responseCode == HttpURLConnection.HTTP_OK)
                        BitmapFactory.decodeStream(conn.inputStream)
                    else null
                }.getOrNull()
            }
            isCapturing.value = false
            when {
                bmp == null -> errorMessage.value = "Couldn't load that image — try another."
                bmp.width < MIN_COVER_DIMENSION_PX || bmp.height < MIN_COVER_DIMENSION_PX ->
                    errorMessage.value =
                        "Too low quality (${bmp.width}×${bmp.height}) — try another."
                else -> pendingBitmap.value = bmp
            }
        }
    }

    fun navigateBack() {
        val wv = webViewRef.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onDismiss()
    }

    BackHandler { navigateBack() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                // ScrollBlockingFrame prevents the WebView from calling requestChildRectangleOnScreen
                // up the view hierarchy. Without this, when the search bar inside the WebView is
                // focused, the WebView asks its parent to scroll/pan the window to reveal the input.
                // The system responds by panning the entire window up, exposing a white gap between
                // the app content and the keyboard. Returning false here keeps the window in place
                // and lets the WebView handle keyboard visibility internally.
                val frame = object : android.widget.FrameLayout(ctx) {
                    override fun requestChildRectangleOnScreen(
                        child: android.view.View,
                        rectangle: android.graphics.Rect,
                        immediate: Boolean,
                    ) = false
                }
                val webView = WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.userAgentString = CHROME_UA
                    webViewRef.value = this

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onImageSelected(url: String) {
                                selectedUrlChannel.trySend(url)
                            }
                        },
                        "AndroidCoverBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            isPageLoading.value = true
                            errorMessage.value = null
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            isPageLoading.value = false
                            view.evaluateJavascript(TAP_BRIDGE_JS, null)
                        }
                    }

                    loadUrl("https://www.bing.com/images/search?q=${Uri.encode(query)}")
                }
                frame.addView(
                    webView,
                    android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                )
                frame
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar — hidden while the keyboard is up, since Bing's own search box sits right
        // underneath it and this bar would otherwise cover whatever you're typing there.
        // The back gesture/button still works normally even while it's hidden.
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        if (!imeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::navigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "Tap a photo to use it as cover art",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::navigateBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }

        // Spinner while the page or the download is loading
        if (isPageLoading.value || isCapturing.value) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White,
            )
        }

        // Error toast at the very bottom when a download fails or the image is rejected for quality
        errorMessage.value?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(err, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Injected into the Bing Images results page. Bing stores each thumbnail's true
// full-resolution source URL as JSON in the result element's "m" attribute (murl),
// so we read that directly instead of guessing from network requests. Runs in the
// capture phase and prevents the default click so the WebView doesn't navigate away
// from the results grid.
private const val TAP_BRIDGE_JS = """
(function() {
  document.addEventListener('click', function(e) {
    var el = e.target.closest('.iusc');
    if (!el) return;
    e.preventDefault();
    e.stopPropagation();
    try {
      var data = JSON.parse(el.getAttribute('m'));
      if (data && data.murl) {
        AndroidCoverBridge.onImageSelected(data.murl);
      }
    } catch (err) {}
  }, true);
})();
"""
