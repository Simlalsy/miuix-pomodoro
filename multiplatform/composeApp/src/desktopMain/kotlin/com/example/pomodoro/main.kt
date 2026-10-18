package com.example.pomodoro

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image

/** 窗口/任务栏图标：与安卓自适应图标同一张 PNG（打包进 jar 的 resources） */
private fun appIcon(): BitmapPainter? = runCatching {
    val bytes = object {}.javaClass.getResourceAsStream("/app-icon.png")?.readBytes() ?: return null
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()

fun main() = application {
    val state = remember { State() }
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    // 截图/调试用：POMODORO_PAGE=timer|stats|todos|settings，POMODORO_THEME=light|dark|aurora|system，POMODORO_ABOUT=1
    remember {
        System.getenv("POMODORO_PAGE")?.let { id ->
            Page.entries.firstOrNull { it.id == id }?.let { state.page = it }
        }
        System.getenv("POMODORO_THEME")?.let { state.theme = it }
        if (System.getenv("POMODORO_ABOUT") == "1") state.showAbout = true
        System.getenv("POMODORO_MODAL")?.let { state.aboutModal = it }
        true
    }
    Window(
        onCloseRequest = ::exitApplication,
        icon = remember { appIcon() },
        title = if (state.running || state.secondsLeft < state.totalSeconds) "${state.timeText} — 番茄钟" else "番茄钟",
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(1100.dp, 780.dp)),
        // 与网页一致：空格快速开始/暂停（输入框聚焦时由输入框消费）、Esc 关闭关于页
        onKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) return@Window false
            when (e.key) {
                Key.Spacebar -> {
                    if (state.running) state.pause() else state.start(scope)
                    true
                }
                Key.Escape -> when {
                    state.aboutModal != null -> {
                        state.aboutModal = null
                        true
                    }
                    state.showAbout -> {
                        state.showAbout = false
                        true
                    }
                    else -> false
                }
                else -> false
            }
        },
    ) { App(state, isDark) }
}
