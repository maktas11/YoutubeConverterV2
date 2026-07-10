package com.maktas.ytconverter.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

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
    // URL detected passively by shouldInterceptRequest — shown in the "Use this?" bar.
    val capturedUrl = remember { mutableStateOf<String?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // shouldInterceptRequest runs on a background thread; use a channel to post to main.
    val interceptChannel = remember { Channel<String>(Channel.CONFLATED) }
    // Download pipeline — receives confirmed URLs from the "Use this" button.
    val downloadChannel = remember { Channel<String>(Channel.CONFLATED) }

    pendingBitmap.value?.let { bmp ->
        pendingBitmap.value = null
        onImageCaptured(bmp)
    }

    // Bridge intercepted URLs from the background thread onto Compose state.
    LaunchedEffect(Unit) {
        for (url in interceptChannel) {
            capturedUrl.value = url
        }
    }

    // Download the confirmed image and pass the bitmap to the crop screen.
    LaunchedEffect(Unit) {
        for (url in downloadChannel) {
            isCapturing.value = true
            errorMessage.value = null
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 12_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("User-Agent", CHROME_UA)
                    conn.setRequestProperty("Referer", "https://www.google.com/")
                    conn.connect()
                    if (conn.responseCode == HttpURLConnection.HTTP_OK)
                        BitmapFactory.decodeStream(conn.inputStream)
                    else null
                }.getOrNull()
            }
            isCapturing.value = false
            if (bmp != null) pendingBitmap.value = bmp
            else errorMessage.value = "Couldn't load that image — try another."
        }
    }

    fun navigateBack() {
        capturedUrl.value = null
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

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            isPageLoading.value = true
                            capturedUrl.value = null
                            errorMessage.value = null
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            isPageLoading.value = false
                        }

                        // Runs on a background thread for every resource the WebView fetches.
                        // Filter for real content images — not Google's encrypted-tbn
                        // thumbnails or gstatic UI assets — and surface them via the channel.
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val url = request.url.toString()
                            if (isContentImage(url)) interceptChannel.trySend(url)
                            return null
                        }
                    }

                    loadUrl("https://www.google.com/search?q=${Uri.encode(query)}&tbm=isch")
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

        // Top bar
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
                "Browse and tap a thumbnail",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ::navigateBack) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Spinner while the page or the download is loading
        if (isPageLoading.value || isCapturing.value) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White,
            )
        }

        // "Use this image?" bottom sheet — covers ~1/3 of the screen.
        // Appears when shouldInterceptRequest detects a content-image URL.
        capturedUrl.value?.let { url ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Drag-handle indicator
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Image found!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "We detected a full-size image. Do you want to use it as cover art?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { capturedUrl.value = null; downloadChannel.trySend(url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use this image", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { capturedUrl.value = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Keep browsing")
                    }
                }
            }
        }

        // Error toast at the very bottom when a download fails
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

// Returns true for real content images we want to offer the user.
// Filters out Google's encrypted-tbn thumbnails, gstatic UI assets, favicons, and SVGs.
// googleusercontent.com (lh3.*) is allowed — that's Google's CDN for the detail-panel image.
private fun isContentImage(url: String): Boolean {
    val lower = url.lowercase()
    if (!url.startsWith("http")) return false
    if (lower.contains("encrypted-tbn")) return false
    if (lower.contains(".gstatic.com")) return false
    if (lower.contains("google.com/")) return false
    if (lower.contains("favicon")) return false
    if (lower.contains(".svg")) return false
    if (lower.contains(".gif")) return false
    return lower.contains(".jpg") || lower.contains(".jpeg") ||
        lower.contains(".png") || lower.contains(".webp") ||
        lower.contains("googleusercontent.com")
}
