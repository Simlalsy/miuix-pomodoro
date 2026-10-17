package com.example.pomodoro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun platformPlaySound() {
    try { java.awt.Toolkit.getDefaultToolkit().beep() } catch (_: Exception) {}
}

actual suspend fun loadImageBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val bytes = if (url.startsWith("data:", ignoreCase = true)) decodeDataUrlBytes(url) else fetchBytes(url)
        bytes?.let { ImageIO.read(ByteArrayInputStream(it))?.toComposeImageBitmap() }
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

actual fun startForegroundService(title: String, text: String) { /* Desktop: no-op */ }

actual fun stopForegroundService() { /* Desktop: no-op */ }

private var trayIcon: java.awt.TrayIcon? = null

/** 桌面通知：系统托盘气泡（Windows 通知中心 / macOS 通知中心 / Linux 通知守护） */
private fun ensureTrayIcon(): java.awt.TrayIcon? {
    trayIcon?.let { return it }
    if (!java.awt.SystemTray.isSupported()) return null
    return try {
        val image = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = java.awt.Color(0x34, 0x82, 0xFF)
        g.fillRoundRect(0, 0, 64, 64, 20, 20)
        g.color = java.awt.Color.WHITE
        g.stroke = java.awt.BasicStroke(5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
        g.drawOval(18, 18, 28, 28)
        g.drawLine(32, 32, 32, 24)
        g.drawLine(32, 32, 39, 37)
        g.dispose()
        val icon = java.awt.TrayIcon(image, "番茄钟").apply { isImageAutoSize = true }
        java.awt.SystemTray.getSystemTray().add(icon)
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { java.awt.SystemTray.getSystemTray().remove(icon) } })
        trayIcon = icon
        icon
    } catch (_: Exception) {
        null
    }
}

actual fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
    onResult(java.awt.SystemTray.isSupported())
}

actual fun platformNotify(title: String, body: String) {
    val icon = ensureTrayIcon() ?: return
    runCatching { icon.displayMessage(title, body, java.awt.TrayIcon.MessageType.NONE) }
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) { /* Desktop no-op */ }

@Composable
actual fun BlurBackdrop(show: Boolean) {
    if (!show) return
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color.Black.copy(alpha = 0.12f),
        Color.Black.copy(alpha = 0.32f),
        Color.Black.copy(alpha = 0.42f)
    ))))
}

/**桌面端使用无操作存储 */
