package com.example.pomodoro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun platformPlaySound()

@Composable
expect fun KeepScreenOn(enabled: Boolean)

@Composable
expect fun BlurBackdrop(show: Boolean)

expect fun startForegroundService(title: String, text: String)
expect fun stopForegroundService()

/** 请求系统通知权限（主线程回调）；已授权或平台无需授权时立即回调 true */
expect fun requestNotificationPermission(onResult: (Boolean) -> Unit)

/** 发送一条系统通知；未授权或平台不支持时静默忽略 */
expect fun platformNotify(title: String, body: String)
