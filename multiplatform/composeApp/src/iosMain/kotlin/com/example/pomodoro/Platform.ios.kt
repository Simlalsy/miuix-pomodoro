package com.example.pomodoro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfURL
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.UIKit.UIApplication
import platform.posix.memcpy

/** iOS 设置存储：NSUserDefaults（对应 Android 的 SharedPreferences） */
class IosSettingsStorage : SettingsStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveInt(key: String, value: Int) { defaults.setInteger(value.toLong(), key) }
    override fun loadInt(key: String, default: Int): Int =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key).toInt()

    override fun saveBoolean(key: String, value: Boolean) { defaults.setBool(value, key) }
    override fun loadBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    override fun saveString(key: String, value: String) { defaults.setObject(value, key) }
    override fun loadString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default
}

@OptIn(ExperimentalForeignApi::class)
actual fun platformPlaySound() {
    // 1104 = Tock：系统短提示音，跟随静音键
    AudioServicesPlaySystemSound(1104u)
}

actual suspend fun loadImageBitmap(url: String): ImageBitmap? = withContext(Dispatchers.Default) {
    try {
        val bytes = if (url.startsWith("data:", ignoreCase = true)) decodeDataUrlBytes(url) else fetchBytes(url)
        bytes?.let { runCatching { org.jetbrains.skia.Image.makeFromEncoded(it) }.getOrNull()?.toComposeImageBitmap() }
    } catch (_: Exception) {
        null
    }
}

/** 同步下载（在 Default 调度器里执行）；失败返回 null */
private fun fetchBytes(url: String): ByteArray? {
    val nsUrl = NSURL.URLWithString(url) ?: return null
    return NSData.dataWithContentsOfURL(nsUrl)?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

actual fun startForegroundService(title: String, text: String) { /* iOS 无前台服务概念 */ }

actual fun stopForegroundService() { /* iOS: no-op */ }

actual fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
    val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(options) { granted, _ ->
        dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
    }
}

actual fun platformNotify(title: String, body: String) {
    val content = UNMutableNotificationContent().apply {
        this.title = title
        this.body = body
        sound = UNNotificationSound.defaultSound
    }
    val id = "pomodoro-" + NSDate().timeIntervalSince1970.toString()
    val request = UNNotificationRequest.requestWithIdentifier(id, content, null)
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
}

@Suppress("DEPRECATION")
actual fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    LaunchedEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) { /* iOS 无系统返回键 */ }

@Composable
actual fun BlurBackdrop(show: Boolean) {
    if (!show) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
}
