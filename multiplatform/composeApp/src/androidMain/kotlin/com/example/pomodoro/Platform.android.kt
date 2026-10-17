package com.example.pomodoro

import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun platformPlaySound() {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        tg.release()
    } catch (_: Exception) {}
}

actual suspend fun loadImageBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val bytes = if (url.startsWith("data:", ignoreCase = true)) decodeDataUrlBytes(url) else fetchBytes(url)
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    } catch (_: Exception) {
        null
    }
}

/** 简单 GET（超时 10s/15s，重定向跟随 HttpURLConnection 默认行为） */
private fun fetchBytes(url: String): ByteArray? {
    val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = true
    }
    return try {
        conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
}

actual fun startForegroundService(title: String, text: String) { /* Handled via MainActivity */ }

actual fun stopForegroundService() { /* Handled via MainActivity */ }

actual fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
    val act = MainActivity.current
    if (act == null) {
        onResult(false)
        return
    }
    act.runOnUiThread { act.requestNotificationPermission { granted -> onResult(granted) } }
}

actual fun platformNotify(title: String, body: String) {
    val act = MainActivity.current ?: return
    if (!act.hasNotificationPermission()) return
    act.runOnUiThread { act.showSystemNotification(title, body) }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    LaunchedEffect(enabled) { view.keepScreenOn = enabled }
}

@Composable
actual fun BlurBackdrop(show: Boolean) {
    if (!show) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .let { modifier ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) modifier.nativeBlur(25f) else modifier
            }
    )
}

/**Android 12+ 原生模糊效果Modifier */
fun Modifier.nativeBlur(radius: Float = 25f): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        this
    }
}

/**Android 12+ 窗口背景模糊 */
fun Window.setBackgroundBlur(blurRadius: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        attributes = attributes.apply {
            this.blurBehindRadius = blurRadius
        }
    }
}
