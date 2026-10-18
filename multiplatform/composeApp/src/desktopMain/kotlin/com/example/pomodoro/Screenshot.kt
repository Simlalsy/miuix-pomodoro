package com.example.pomodoro

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.pomodoro.ui.AboutPage
import com.example.pomodoro.ui.StatsPage
import com.example.pomodoro.ui.SettingsPage
import com.example.pomodoro.ui.TimerPage
import com.example.pomodoro.ui.TodosPage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * 离屏渲染各页面并导出 PNG —— 用于与网页版截图做像素级比对，无需真实窗口。
 *
 * 运行：`gradlew :composeApp:screenshots`
 * 参数（可选）：`-PshotSizes=1280x800,393x852`、`-PshotDensity=2`
 */
fun main(args: Array<String>) {
    val outDir = File(args.getOrNull(0) ?: "build/screenshots").apply { mkdirs() }
    // 环形呼吸等无限动画在截图里冻结到基准相位，与 tools/webshot.js 注入的 animation:none 对应
    State.debugFreezeAnim = true
    // 宽度/高度按**像素**给出（density 倍率下）：desktop 用 2560×1600@2 = 1280×800dp，
    // 手机用 786×1704@2 = 393×852dp
    val width = args.getOrNull(1)?.toIntOrNull() ?: 2560
    val height = args.getOrNull(2)?.toIntOrNull() ?: 1600
    val density = args.getOrNull(3)?.toFloatOrNull() ?: 2f

    // 每个用例：页面 + 主题 +（可选）状态构造（运行中 / 有待办 / 关于页…），
    // 与 tools/webshot.js 的注入状态一一对应
    data class Case(
        val name: String,
        val page: Page,
        val theme: String,
        val frames: Int = 30,
        val setup: (State) -> Unit = {},
    )

    val cases = listOf(
        Case("desktop-timer-dark", Page.Timer, "dark"),
        Case("desktop-timer-light", Page.Timer, "light"),
        Case("desktop-timer-aurora", Page.Timer, "aurora"),
        Case("desktop-timer-running", Page.Timer, "dark") { it.debugTimerState(remaining = 687, running = true, cycle = 2) },
        // 模式色：进度弧/呼吸点/迷你环随模式切换（网页 updateModeUI()），0.5s stroke 过渡
        Case("desktop-timer-short", Page.Timer, "light") { it.selectMode(TimerMode.Short) },
        Case("desktop-timer-long", Page.Timer, "dark") { it.selectMode(TimerMode.Long) },
        Case("desktop-stats-dark", Page.Stats, "dark"),
        Case("desktop-stats-light", Page.Stats, "light"),
        Case("desktop-todos-light", Page.Todos, "light"),
        Case("desktop-todos-dark", Page.Todos, "dark"),
        Case("desktop-todos-items", Page.Todos, "light") {
            it.addTodo("读完《重构》第 3 章")
            it.addTodo("写周报")
            it.addTodo("晚上跑步 5km")
            it.toggleTodo(0)
        },
        Case("desktop-settings-light", Page.Settings, "light"),
        Case("desktop-settings-dark", Page.Settings, "dark"),
        Case("desktop-about-light", Page.Timer, "light", frames = 45) { it.showAbout = true },
    )

    cases.forEach { case ->
        val state = State(NoOpStorage)
        state.page = case.page
        state.theme = case.theme
        case.setup(state)
        renderToFile(File(outDir, "${case.name}.png"), width, height, density, frames = case.frames) {
            App(state, case.theme != "light", showSplash = false)
        }
        println("saved ${case.name}.png")
    }

    // 自定义背景图用例：用 data:image PNG（离线可复现）预载，验证「下载 → 解码 → 图层」全链路
    runBlocking {
        val state = State(NoOpStorage)
        state.page = Page.Timer
        state.theme = "light"
        state.applyBackgroundUrl(dataUrlOfTestPng())
        state.toast = null
        renderToFile(File(outDir, "desktop-timer-light-bg.png"), width, height, density, frames = 48) {
            App(state, false, showSplash = false)
        }
        println("saved desktop-timer-light-bg.png")
    }
}

/** 生成一张 96×96 渐变测试 PNG 并包成 data URL（不依赖网络） */
private fun dataUrlOfTestPng(): String {
    val img = java.awt.image.BufferedImage(96, 96, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(0x2B, 0x5C, 0xC8), 96f, 96f, java.awt.Color(0x11, 0x14, 0x33))
    g.fillRect(0, 0, 96, 96)
    g.dispose()
    val out = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(img, "png", out)
    return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray())
}

private fun renderToFile(file: File, width: Int, height: Int, density: Float, frames: Int = 30, content: @androidx.compose.runtime.Composable () -> Unit) {
    val scene = ImageComposeScene(width, height, Density(density)) { content() }
    try {
        // 连续推进几帧，让入场动画（alpha/位移）落定后再导出
        var t = 0L
        repeat(frames) {
            scene.render(t)
            t += 16_000_000L
        }
        val image = scene.render(t)
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode failed")
        file.writeBytes(data.bytes)
    } finally {
        scene.close()
    }
}

/** 供截图脚本使用：把某个页面单独渲染成整屏内容 */
@androidx.compose.runtime.Composable
fun PagePreview(state: State, content: @androidx.compose.runtime.Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) { content() }
    }
}
