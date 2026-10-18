package com.example.pomodoro

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.pomodoro.ui.AppFonts

/**
 * 设计令牌：**逐条对应** `index.html` 里 `:root` / `[data-theme]` 的 CSS 变量，
 * 保证原生实现与网页版（以及内嵌同一份网页的安卓版）取色一致。
 */
@Immutable
data class AppColors(
    /** 深色系（含极光） */
    val dark: Boolean,
    /** 极光主题：页面自带渐变背景、卡片为毛玻璃 */
    val aurora: Boolean,
    /** 背景图激活：卡片/分段等切毛玻璃（对应网页 html[data-bg-active]） */
    val bgActive: Boolean = false,

    val primary: Color,
    val onPrimary: Color,
    /** --miuix-primary-container：柱状图渐变末端（默认强调色为静态值，自定义强调色是 shade(accent,18)） */
    val primaryContainer: Color,
    /** --miuix-soft：强调色的半透明容器（图标底、选中态） */
    val soft: Color,

    val background: Color,
    val surface: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,

    val onSurface: Color,
    /** --miuix-on-surface-variant */
    val variant: Color,
    val outline: Color,
    val divider: Color,

    val short: Color,
    val long: Color,
    val error: Color,
    val dangerSoft: Color,

    val shadow: Color,
    val shadowLg: Color,
)

/** 网页把强调色写进内联样式：primary = accent，soft = accent@14%（浅）/22%（深） */
fun accentColors(accent: Color, dark: Boolean) = accent to accent.copy(alpha = if (dark) 0.22f else 0.14f)

/** 与网页 shade(hex, pct) 一致：向白色混合 pct% */
fun shade(c: Color, pct: Float): Color {
    val p = kotlin.math.abs(pct) / 100f
    val t = if (pct < 0f) 0f else 1f
    fun mix(v: Float) = (t - v) * p + v
    return Color(mix(c.red), mix(c.green), mix(c.blue), c.alpha)
}

private val LightColors = AppColors(
    dark = false, aurora = false,
    primary = Color(0xFF3482FF), onPrimary = Color.White, primaryContainer = Color(0xFF5D9BFF), soft = Color(0x1F3482FF),
    background = Color(0xFFF4F5F7), surface = Color.White, container = Color.White,
    containerHigh = Color(0xFFECEDF0), containerHighest = Color(0xFFE4E5E8),
    onSurface = Color(0xFF0F0F0F), variant = Color(0xFF959595),
    outline = Color(0xFFD9D9D9), divider = Color(0xFFE6E6E6),
    short = Color(0xFF36D167), long = Color(0xFF00BCD4),
    error = Color(0xFFE94634), dangerSoft = Color(0x1AE94634),
    shadow = Color(0x0F000000), shadowLg = Color(0x1A000000),
)

private val DarkColors = AppColors(
    dark = true, aurora = false,
    primary = Color(0xFF277AF7), onPrimary = Color.White, primaryContainer = Color(0xFF338FE4), soft = Color(0x33277AF7),
    background = Color(0xFF1A1A1C), surface = Color(0xFF242426), container = Color(0xFF2A2A2D),
    containerHigh = Color(0xFF323236), containerHighest = Color(0xFF3A3A3F),
    onSurface = Color(0xFFE6E6E6), variant = Color(0xFF8C93B0),
    outline = Color(0xFF4A4A4E), divider = Color(0xFF3A3A3E),
    short = Color(0xFF36D167), long = Color(0xFF00BCD4),
    error = Color(0xFFF12522), dangerSoft = Color(0x29F12522),
    shadow = Color(0x59000000), shadowLg = Color(0x8C000000),
)

private val AuroraColors = AppColors(
    dark = true, aurora = true,
    primary = Color(0xFF8B7BFF), onPrimary = Color.White, primaryContainer = Color(0xFF6C5CE7), soft = Color(0x388B7BFF),
    background = Color(0xFF0B0D1F), surface = Color(0x14FFFFFF), container = Color(0x12FFFFFF),
    containerHigh = Color(0x1FFFFFFF), containerHighest = Color(0x29FFFFFF),
    onSurface = Color(0xFFEEF0FF), variant = Color(0xFFAAB3E2),
    outline = Color(0x24FFFFFF), divider = Color(0x1AFFFFFF),
    short = Color(0xFF36D167), long = Color(0xFF00BCD4),
    error = Color(0xFFFF6B81), dangerSoft = Color(0x2EFF6B81),
    shadow = Color(0x52000000), shadowLg = Color(0x75000000),
)

fun appColors(theme: String, systemDark: Boolean, accent: String, bgActive: Boolean = false): AppColors {
    val base = when (theme) {
        "light" -> LightColors
        "dark" -> DarkColors
        "aurora" -> AuroraColors
        else -> if (systemDark) DarkColors else LightColors
    }
    val acc = accentColorOrNull(accent)
    val (p, soft) = if (acc != null) accentColors(acc, base.dark) else base.primary to base.soft
    return base.copy(
        primary = p,
        soft = soft,
        primaryContainer = if (acc != null) shade(acc, 18f) else base.primaryContainer,
        bgActive = bgActive,
    )
}

fun accentColorOrNull(hex: String): Color? = runCatching {
    val h = hex.removePrefix("#")
    if (h.length != 6) return null
    Color(0xFF000000L or h.toLong(16))
}.getOrNull()

/** 设置页「强调色」可选项（与网页 KEY_COLORS 一致） */
val KEY_COLORS: List<Pair<String, String>> = listOf(
    "蓝" to "#3482FF",
    "绿" to "#36D167",
    "紫" to "#7C4DFF",
    "黄" to "#FFB21D",
    "橙" to "#FF5722",
    "粉" to "#E91E63",
    "青" to "#00BCD4",
)

@Immutable
data class AppTypography(
    val font: FontFamily,
    val symbols: FontFamily,
)

val LocalAppColors = staticCompositionLocalOf { LightColors }
val LocalAppType = staticCompositionLocalOf<AppTypography> { error("PomodoroTheme 未初始化") }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val font: FontFamily
        @Composable @ReadOnlyComposable get() = LocalAppType.current.font

    val symbols: FontFamily
        @Composable @ReadOnlyComposable get() = LocalAppType.current.symbols
}

@Composable
fun PomodoroTheme(theme: String, systemDark: Boolean, accent: String, bgActive: Boolean = false, content: @Composable () -> Unit) {
    val target = appColors(theme, systemDark, accent, bgActive)
    // 网页切换主题时是元素级 CSS 过渡（body .55s、.card .4s、.topbar .5s、.rail .4s、按钮 .3s…），
    // 而不是整体瞬切；这里用一份调色板插值统一复刻（0.45s，取中间值，其余由 themeWash 覆盖）
    val fade = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (to == target) return@LaunchedEffect
        // 从「当前可见色」开始过渡（连续切换时不会跳变）
        from = lerpColors(from, to, fade.value)
        to = target
        fade.snapTo(0f)
        fade.animateTo(1f, tween(450, easing = CssEase))
    }
    val colors = lerpColors(from, to, fade.value)
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides AppTypography(AppFonts.inter, AppFonts.symbols),
        content = content,
    )
}

/** 两套调色板按进度插值（结构位 dark/aurora/bgActive 立刻取新值，与网页 `[data-theme]` 切换一致） */
internal fun lerpColors(a: AppColors, b: AppColors, t: Float): AppColors {
    if (t >= 1f) return b
    fun l(x: Color, y: Color) = androidx.compose.ui.graphics.lerp(x, y, t)
    return b.copy(
        primary = l(a.primary, b.primary),
        onPrimary = l(a.onPrimary, b.onPrimary),
        primaryContainer = l(a.primaryContainer, b.primaryContainer),
        soft = l(a.soft, b.soft),
        background = l(a.background, b.background),
        surface = l(a.surface, b.surface),
        container = l(a.container, b.container),
        containerHigh = l(a.containerHigh, b.containerHigh),
        containerHighest = l(a.containerHighest, b.containerHighest),
        onSurface = l(a.onSurface, b.onSurface),
        variant = l(a.variant, b.variant),
        outline = l(a.outline, b.outline),
        divider = l(a.divider, b.divider),
        short = l(a.short, b.short),
        long = l(a.long, b.long),
        error = l(a.error, b.error),
        dangerSoft = l(a.dangerSoft, b.dangerSoft),
        shadow = l(a.shadow, b.shadow),
        shadowLg = l(a.shadowLg, b.shadowLg),
    )
}

/** 网页里用到的 font-weight 取值别名 */
object W {
    val normal = FontWeight(400)
    val medium = FontWeight(500)
    val semi = FontWeight(600)
    val bold = FontWeight(700)
    val extra = FontWeight(800)
}

/**
 * 网页 `:root` 里的缓动曲线，逐条对应：
 * `--spring: cubic-bezier(0.34, 1.56, 0.64, 1)`（弹性、会过冲）
 * `--ease: cubic-bezier(0.4, 0, 0.2, 1)`
 * `--nav-ease: cubic-bezier(0.4, 1.2, 0.95, 0.97)`
 * 波纹用 `cubic-bezier(0.22, 1, 0.36, 1)`。
 */
internal val CssSpring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
internal val CssEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
internal val CssRippleEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * CSS `line-height: normal` 在真实字体栈下的实测行盒高度（px = dp）。
 * 数据来自 tools/web_metrics.js 的行盒探针（14→18、15→19、26→33 …）；
 * 中文回退字体（Windows 雅黑 / 安卓 MiSans）比 Inter 高约 5%，含中文的行取回退值。
 */
internal fun normalLine(size: Int): TextUnit = when (size) {
    11 -> 14
    12 -> 15
    13 -> 16
    14 -> 18
    15 -> 19
    16 -> 20
    17 -> 21
    18 -> 23
    19 -> 24
    20 -> 25
    21 -> 26
    22 -> 28
    23 -> 29
    24 -> 30
    25 -> 32
    26 -> 33
    28 -> 35
    30 -> 38
    34 -> 43
    40 -> 51
    44 -> 56
    56 -> 71
    64 -> 81
    else -> kotlin.math.round(size * 1.266).toInt()
}.sp

/** 纯拉丁/数字文本的行盒：Inter (1984+494)/2048 = 1.20996 ≈ 1.21 */
internal fun latinLine(size: Int): TextUnit = kotlin.math.round(size * 1.20996f).sp
