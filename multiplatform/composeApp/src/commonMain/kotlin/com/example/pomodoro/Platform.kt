package com.example.pomodoro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun platformPlaySound()

@Composable
expect fun KeepScreenOn(enabled: Boolean)

@Composable
expect fun BlurBackdrop(show: Boolean)

/** 系统返回（仅 Android）：用于关闭关于页等浮层，返回 false 时交回系统 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** 系统栏图标明暗跟随页面主题（网页由 MiuixBridge.onThemeChanged 同步） */
@Composable
expect fun PlatformSystemBars(dark: Boolean)

/** 打开外部链接（关于页的外链行，对应网页 openExternal()） */
expect fun openExternalUrl(url: String)

expect fun startForegroundService(title: String, text: String)
expect fun stopForegroundService()

/** 请求系统通知权限（主线程回调）；已授权或平台无需授权时立即回调 true */
expect fun requestNotificationPermission(onResult: (Boolean) -> Unit)

/** 发送一条系统通知；未授权或平台不支持时静默忽略 */
expect fun platformNotify(title: String, body: String)
