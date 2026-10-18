package com.example.pomodoro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.example.pomodoro.AppTheme
import com.example.pomodoro.AppColors
import com.example.pomodoro.CardGlass
import com.example.pomodoro.CssEase
import com.example.pomodoro.CssRippleEase
import com.example.pomodoro.CssSpring
import com.example.pomodoro.LocalCardGlass
import com.example.pomodoro.LocalCompact
import com.example.pomodoro.LocalPageEntry
import com.example.pomodoro.LocalScreenWidth
import com.example.pomodoro.W
import com.example.pomodoro.auroraBackdrop
import com.example.pomodoro.latinLine
import com.example.pomodoro.normalLine

/* ============================================================
 * 通用组件：尺寸/圆角/字重全部对应 index.html 的 CSS 规格
 * ============================================================ */

/**
 * 背景图激活时（对应网页 html[data-bg-active]）把底色按 color-mix 的百分比混透明：
 * color-mix(in srgb, token N%, transparent) 等价于 alpha *= N%；未激活时原样返回。
 */
@Composable
internal fun glassDim(color: Color, pct: Float): Color {
    val c = AppTheme.colors
    return if (c.bgActive) color.copy(alpha = color.alpha * pct) else color
}

/**
 * 网页 `filter: brightness(N)` 的等价值（逐通道乘系数并钳到 0–1）。
 * 用于 `.btn-primary:hover { filter: brightness(1.05) }`。
 */
internal fun brighten(color: Color, factor: Float): Color = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
    alpha = color.alpha,
)

/** 网页 `:hover` 的底色：极光主题用白色低透明度覆盖，背景图模式按 60% 混透明 */
@Composable
internal fun hoverSurface(level: Int): Color {
    val c = AppTheme.colors
    return when {
        c.aurora -> Color.White.copy(alpha = if (level >= 1) 0.16f else 0.12f)
        c.bgActive -> glassDim(c.containerHigh, 0.60f)
        else -> if (level >= 1) c.containerHighest else c.containerHigh
    }
}

private class RippleSpec {
    var center: Offset = Offset.Zero
    var diameter: Float = 0f
}

/**
 * 复刻网页 `.ripple`（MIUI 触感）：按下处半径 = 元素长边/2、scale 0→3、
 * opacity .5 → .25（60%）→ 0，0.7s `cubic-bezier(.22,1,.36,1)`。
 * 只观察 Initial 事件、不消费，避免与 clickable 抢手势。
 */
internal fun Modifier.miuixRipple(color: Color): Modifier = composed {
    val spec = remember { RippleSpec() }
    val anim = remember { Animatable(1f) }
    val trigger = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(trigger.intValue) {
        if (trigger.intValue == 0) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = CssRippleEase))
    }
    this
        .pointerInput(Unit) {
            var wasPressed = false
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.pressed }
                    if (change != null && !wasPressed) {
                        spec.center = change.position
                        spec.diameter = kotlin.math.max(size.width, size.height).toFloat()
                        trigger.intValue++
                    }
                    wasPressed = change != null
                }
            }
        }
        .drawWithContent {
            drawContent()
            val p = anim.value
            if (p < 1f) {
                val alpha = if (p < 0.6f) 0.5f - 0.25f * (p / 0.6f) else 0.25f * (1f - (p - 0.6f) / 0.4f)
                drawCircle(
                    color = color.copy(alpha = color.alpha * 0.22f * alpha),
                    radius = spec.diameter * 1.5f * p,
                    center = spec.center,
                )
            }
        }
}

/**
 * 毛玻璃底色：极光主题下网页用的是各自的白透明度值（而非统一的 surface-container）：
 *   .segment rgba(255,255,255,.08) / .stepper .08 / .tone-chip .08 / .btn-ghost .1 /
 *   .switch .14 / .segment button.active .16 / .ring-track .16 / .card .07
 * 原生无法做真正的 backdrop 采样（Compose 无背层模糊），但底色、描边与阴影可完全对齐；
 * 极光背景本身是低频渐变，模糊后与不模糊的视觉差异极小。
 */
@Composable
private fun glassSurface(level: Int): Color {
    val c = AppTheme.colors
    val base = if (c.aurora) {
        Color.White.copy(alpha = when (level) {
            2 -> 0.16f  // .segment button.active（极光）
            3 -> 0.14f  // .switch（极光）
            1 -> 0.10f  // .btn-ghost（极光）
            else -> 0.08f
        })
    } else {
        when (level) {
            2 -> c.surface            // .segment button.active
            3 -> c.containerHighest   // .switch 轨道
            else -> c.containerHigh   // .segment / .stepper / .tone-chip / .btn-ghost
        }
    }
    // [data-bg-active]：.segment/.stepper/.tone-chip 58%、.btn-ghost 52%、选中项 60%；
    // .switch 网页未定义规则，保持不变
    val pct = when (level) {
        1 -> 0.52f
        2 -> 0.60f
        3 -> 1f
        else -> 0.58f
    }
    return glassDim(base, pct)
}

/**
 * CSS `box-shadow` 复刻：向下偏移 + 高斯模糊 + 挖掉元素本体（box-shadow 不画进 border box）。
 * [blurRadius] 是高斯 σ（CSS blur-radius 的一半）。
 */
@Composable
internal fun RoundedShadow(
    modifier: Modifier,
    color: Color,
    cornerRadius: Dp,
    offsetY: Dp,
    blurRadius: Dp,
) {
    Box(
        modifier
            .drawWithContent {
                val hole = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(0f, 0f, size.width, size.height),
                            CornerRadius(cornerRadius.toPx()),
                        ),
                    )
                }
                clipPath(hole, ClipOp.Difference) {
                    translate(0f, offsetY.toPx()) { this@drawWithContent.drawContent() }
                }
            }
            .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
            .background(color, RoundedCornerShape(cornerRadius)),
    )
}

/** 图标（Material Symbols Rounded 字形，与网页同字体同设置） */
@Composable
fun Symbol(name: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // 网页 .material-symbols-rounded 是 line-height:1，行盒正好等于字号；
    // Compose 里显式 lineHeight 小于字体自然行高时行盒仍会被撑到 ≈1.19em，
    // 放进 Rows/Columns 就会多占≈19% 高度（侧栏项高 67→72、逐项漂移），用定高盒钉住
    Box(modifier.height(size), contentAlignment = Alignment.Center) {
        Text(
            text = SYMBOLS[name].orEmpty(),
            // 基线同样要减半行距：图标字体 (1056+96)/960 = 1.2 → 0.1em（网页 line-height:1 的负半行距）
            modifier = Modifier.offset(y = -(size * 0.1f)),
            color = color,
            fontSize = with(density) { size.toSp() },
            fontFamily = AppTheme.symbols,
            style = TextStyle(lineHeight = with(density) { size.toSp() }),
            maxLines = 1,
        )
    }
}

/** 卡片：bg surface-container / 1px divider 描边 / 圆角 20 / 内边距 26 / 阴影 */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    padding: Dp = Dp.Unspecified,
    radius: Dp = Dp.Unspecified,
    /** 是否播放网页 cardIn 入场（时钟卡片自己给环做 popIn，故关掉整块动画） */
    entry: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val compact = LocalCompact.current
    // 网页 .card：桌面 padding 26 / radius 20；窄屏 20 / 18
    val pad = if (padding.isSpecified) padding else if (compact) 20.dp else 26.dp
    val rad = if (radius.isSpecified) radius else if (compact) 18.dp else 20.dp
    // [data-bg-active]：卡片底色 52%、描边 70%（对应网页 .card）
    val cardBg = glassDim(c.container, 0.52f)
    // 先算好卡片的 modifier：此时还未提供 LocalCardGlass，卡片自身不会重复叠底色
    val cardModifier = Modifier
        .clip(RoundedCornerShape(rad))
        // 网页 [data-theme=aurora] .card backdrop-filter: blur(16px) saturate(1.5)
        .auroraBackdrop(1.5f)
        .background(cardBg)
        .border(1.dp, glassDim(c.divider, 0.70f), RoundedCornerShape(rad))
        // 网页 .card 是 border-box：1px 边框占掉内边距（内容整体 +1）
        .padding(pad + 1.dp)
    Box(modifier) {
        // 网页 .card box-shadow：极光 0 12px 36px rgba(0,0,0,.32)、其余 0 8px 24px。
        // Modifier.shadow 无偏移且强度不足（实测几乎不可见），用自绘阴影复刻。
        RoundedShadow(
            modifier = Modifier.matchParentSize(),
            color = c.shadow,
            cornerRadius = rad,
            offsetY = if (c.aurora) 12.dp else 8.dp,
            // CSS blur-radius 是 2σ：36px→σ18、24px→σ12
            blurRadius = if (c.aurora) 18.dp else 12.dp,
        )
        CompositionLocalProvider(
            LocalCardGlass provides if (c.aurora) CardGlass(cardBg, 1.5f) else null,
        ) {
            Column(modifier = cardModifier) {
                val entryAnim = LocalPageEntry.current
                val cardEntry = entryAnim.card.value
                Column(
                    Modifier.then(
                        if (entry && cardEntry < 0.999f) {
                            Modifier.graphicsLayer {
                                val p = entryAnim.card.value
                                alpha = p.coerceIn(0f, 1f)
                                translationY = (1f - p) * 14.dp.toPx()
                                val s = 0.99f + 0.01f * p
                                scaleX = s; scaleY = s
                            }
                        } else Modifier,
                    ),
                    content = content,
                )
            }
        }
    }
}

/** 卡片标题：<strong> 部分用 onSurface（浏览器 bolder → 命中 800 字重）/ 其余用 onSurfaceVariant，15sp/700 */
@Composable
fun CardTitle(strong: String, rest: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.height(19.dp)) {
        Text(strong, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 15.sp, lineHeight = normalLine(15), fontWeight = W.bold, letterSpacing = 0.2.sp)
        Text(rest, color = c.variant, fontFamily = AppTheme.font, fontSize = 15.sp, lineHeight = normalLine(15), fontWeight = W.bold, letterSpacing = 0.2.sp)
    }
}

/** 分段控件：容器 radius 16 + padding 4 + gap 4；按钮 padding 10/14、行高 18（同网页 .segment button，高 38 → 容器 46） */
@Composable
fun Segment(
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val c = AppTheme.colors
    // 网页 @media(max-width:360px)：.segment button padding 9px 8px / font-size 13px
    val narrow = LocalScreenWidth.current <= 360.dp
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            // 网页 [data-theme=aurora] .segment backdrop-filter: blur(8px)（无 saturate）
            .auroraBackdrop(1f)
            .background(glassSurface(0))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val active = i == selectedIndex
            val bg by animateColorAsState(if (active) glassSurface(2) else Color.Transparent, tween(350, easing = CssEase))
            val fg by animateColorAsState(if (active) c.onSurface else c.variant, tween(350, easing = CssEase))
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val press by animateFloatAsState(if (pressed) 0.96f else 1f, tween(350, easing = CssSpring))
            Box(Modifier.weight(1f)) {
                // 网页 .segment button.active box-shadow：极光 0 2px 10px rgba(0,0,0,.28)、
                // 其余主题 0 2px 8px rgba(0,0,0,.08)
                if (active) {
                    RoundedShadow(
                        modifier = Modifier.matchParentSize(),
                        color = Color.Black.copy(alpha = if (c.aurora) 0.28f else 0.08f),
                        cornerRadius = 12.dp,
                        offsetY = 2.dp,
                        blurRadius = if (c.aurora) 5.dp else 4.dp,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (press < 0.999f) Modifier.scale(press) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                        .miuixRipple(fg),
                    contentAlignment = Alignment.Center,
                ) {
                    // 选中态下方的主色条（growbar：从左向右 scaleX 展开）
                    // 注意：条要相对“按钮整个盒子”定位（同网页 ::before 的 absolute），
                    // 所以按钮的垂直 padding 放到文字上，别放在 Box 上（否则条会贴到内容盒底部=文字中线）
                    if (active) {
                        val p = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { p.animateTo(1f, tween(300, easing = CssSpring)) }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(3.dp)
                                .graphicsLayer {
                                    scaleX = p.value
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .clip(RoundedCornerShape(2.dp))
                                .background(c.primary),
                        )
                    }
                    Text(
                        label,
                        color = fg,
                        fontFamily = AppTheme.font,
                        fontSize = (if (narrow) 13 else 14).sp,
                        lineHeight = normalLine(if (narrow) 13 else 14),
                        fontWeight = W.semi,
                        modifier = Modifier
                            .padding(vertical = if (narrow) 9.dp else 10.dp)
                            // 钉住行盒：13sp 行盒在 Skia 下会多出 0.5dp，窄屏会把胶囊撑高 1px
                            .height(if (narrow) 16.dp else 18.dp),
                    )
                }
            }
        }
    }
}

/** 步进器：容器 radius 12 + padding 4；按钮 34×34 radius 9；数字等宽 Inter */
@Composable
fun Stepper(value: Int, onDelta: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(glassSurface(0)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepperButton("remove") { onDelta(-1) }
        Box(Modifier.widthIn(min = 44.dp), contentAlignment = Alignment.Center) {
            Text(
                "$value",
                color = c.onSurface,
                fontFamily = AppTheme.font,
                fontSize = 16.sp,
                lineHeight = normalLine(16),
                fontWeight = W.bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
        }
        StepperButton("add") { onDelta(1) }
    }
}

@Composable
private fun StepperButton(icon: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, tween(300, easing = CssSpring))
    // 网页 .stepper button:hover { background: container-highest }（极光：白 16%）
    val bg by animateColorAsState(if (hovered) hoverSurface(1) else Color.Transparent, tween(250, easing = CssEase))
    Box(
        Modifier
            .size(34.dp)
            .scale(scale)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .miuixRipple(c.onSurface),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, 20.dp, c.onSurface) }
}

/** 开关：50×30 胶囊，圆形滑块 24，开启位移 20；滑块 0.3s 弹性、按下缩放 0.94 */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    val track by animateColorAsState(if (checked) c.primary else glassSurface(3), tween(300, easing = CssEase))
    val knob by animateColorAsState(if (checked) c.onPrimary else c.onSurface, tween(300, easing = CssEase))
    val offset by animateFloatAsState(if (checked) 20f else 0f, tween(300, easing = CssSpring))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(300, easing = CssSpring))
    Box(
        Modifier
            .size(width = 50.dp, height = 30.dp)
            .scale(scale)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .clickable(interactionSource = interaction, indication = null) { onChange(!checked) }
            .miuixRipple(c.onSurface),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(24.dp)
                .graphicsLayer { translationX = offset.dp.toPx() }
                .shadow(2.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

/** 无文字图标按钮（顶栏/背景）：44×44 radius 14；悬停底色 + 上移 1px（网页 .icon-btn:hover） */
@Composable
fun IconBtn(icon: String, size: Dp = 44.dp, iconSize: Dp = 24.dp, tint: Color? = null, bg: Color = Color.Transparent, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(350, easing = CssSpring))
    val bgColor by animateColorAsState(if (hovered) hoverSurface(0) else bg, tween(300, easing = CssEase))
    val lift by animateDpAsState(if (hovered) (-1).dp else 0.dp, tween(350, easing = CssSpring))
    Box(
        Modifier
            .size(size)
            .offset(y = lift)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .miuixRipple(tint ?: c.onSurface),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, iconSize, tint ?: c.onSurface) }
}

/** 幽灵按钮（重置/跳过）：52×52 radius 18，bg container-high；悬停 container-highest */
@Composable
fun GhostButton(icon: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(350, easing = CssSpring))
    val bg by animateColorAsState(if (hovered) hoverSurface(1) else glassSurface(1), tween(300, easing = CssEase))
    Box(
        Modifier
            .size(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            // 网页 [data-theme=aurora] .btn-ghost backdrop-filter: blur(8px)
            .auroraBackdrop(1f)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .miuixRipple(c.onSurface),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, 24.dp, c.onSurface) }
}

/**
 * 宽幽灵按钮（设置页「应用 / 上传本地图片 / 换一张」等）：对应网页
 * `.btn.btn-ghost` 的自适应宽度形态（height 46、padding 0 18、字号 14、图标 20）。
 */
@Composable
fun GhostWideButton(text: String, icon: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(350, easing = CssSpring))
    val bg by animateColorAsState(if (hovered) hoverSurface(1) else glassSurface(1), tween(300, easing = CssEase))
    Row(
        Modifier
            .height(46.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            // 网页 [data-theme=aurora] .btn-ghost backdrop-filter: blur(8px)
            .auroraBackdrop(1f)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .miuixRipple(c.onSurface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(icon, 20.dp, c.onSurface)
        Text(text, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = normalLine(14), fontWeight = W.bold)
    }
}

/** 主按钮（开始/暂停/添加 等）：高 60，左右内边距 34，radius 18，主色 + 主色阴影 */
@Composable
fun PrimaryButton(
    text: String,
    icon: String? = null,
    running: Boolean = false,
    height: Dp = 60.dp,
    fontSize: Int = 18,
    paddingHorizontal: Dp = 34.dp,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val compact = LocalCompact.current
    // 网页窄屏 .btn-primary：height 56 / padding 0 26
    val h = if (compact && height == 60.dp) 56.dp else height
    val hp = if (compact && paddingHorizontal == 34.dp) 26.dp else paddingHorizontal
    val bg by animateColorAsState(if (running) c.containerHigh else c.primary, tween(300, easing = CssEase))
    val fg by animateColorAsState(if (running) c.onSurface else c.onPrimary, tween(300, easing = CssEase))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(350, easing = CssSpring))
    // 网页 .btn-primary:hover { filter: brightness(1.05) }
    val hoverBg by animateColorAsState(
        if (hovered && !running) brighten(c.primary, 1.05f) else Color.Unspecified,
        tween(300, easing = CssEase),
    )
    // 网页 popIcon()：开始/暂停时图标 iconPop 0.42s var(--spring)
    // （scale .5→1.18@60%→1、rotate -25°→8°→0、opacity 0→1）
    val iconPop = remember { Animatable(1f) }
    var seenRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (seenRunning == running) return@LaunchedEffect
        seenRunning = running
        iconPop.snapTo(0f)
        iconPop.animateTo(1f, tween(420, easing = CssSpring))
    }
    // 网页 .btn-primary box-shadow: 0 10px 24px primary@35%（仅向下偏移，无环境光）；
    // Compose 的 Modifier.shadow 会带 ambient（四周都有），用偏移+模糊层精确复刻
    Box(Modifier.height(h).scale(scale)) {
        if (!running) {
            Box(
                Modifier.matchParentSize().offset(y = 10.dp).blur(12.dp, BlurredEdgeTreatment.Unbounded)
                    .background(c.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
            )
        }
        Row(
            Modifier
                .height(h)
                .clip(RoundedCornerShape(18.dp))
                .background(if (hoverBg == Color.Unspecified) bg else hoverBg)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .miuixRipple(fg)
                .padding(horizontal = hp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Box(
                    Modifier.graphicsLayer {
                        val p = iconPop.value
                        if (p < 0.999f) {
                            val s = if (p < 0.6f) 0.5f + (1.18f - 0.5f) * (p / 0.6f) else 1.18f + (1f - 1.18f) * ((p - 0.6f) / 0.4f)
                            val deg = if (p < 0.6f) -25f + 33f * (p / 0.6f) else 8f - 8f * ((p - 0.6f) / 0.4f)
                            scaleX = s; scaleY = s
                            rotationZ = deg
                            alpha = (p / 0.6f).coerceIn(0f, 1f)
                        }
                    },
                ) { Symbol(icon, if (fontSize >= 16) 26.dp else 20.dp, fg) }
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, fontFamily = AppTheme.font, fontSize = fontSize.sp, lineHeight = normalLine(fontSize), fontWeight = W.bold)
        }
    }
}

/** 统计单元格：bg surface，radius 16，padding 20/18，数值 28sp/800 主色、等宽 */
@Composable
fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    // 网页 @media(max-width:360px)：.stat-cell padding 14 / .val 24px
    val narrow = LocalScreenWidth.current <= 360.dp
    // 网页 setStat()：数值变化时 .val.bump（0 → 40% 缩放 1.22 → 1，0.5s var(--spring)）
    val bump = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (previous != value) {
            previous = value
            bump.snapTo(1f)
            bump.animateTo(1.22f, tween(200, easing = CssSpring))
            bump.animateTo(1f, tween(300, easing = CssSpring))
        }
    }
    // 网页 .stat-cell:hover { transform: translateY(-2px) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateDpAsState(if (hovered) (-2).dp else 0.dp, tween(350, easing = CssSpring))
    Column(
        modifier
            .hoverable(interaction)
            .offset(y = lift)
            .clip(RoundedCornerShape(16.dp))
            // 网页 [data-theme=aurora] .stat-cell backdrop-filter: blur(12px) saturate(1.4)
            .auroraBackdrop(1.4f)
            // [data-bg-active]：.stat-cell 底色 52%
            .background(glassDim(c.surface, 0.52f))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            // 网页 .stat-cell 是 border-box：1px 边框占掉内边距（内容从 +1 开始）
            .padding(
                horizontal = if (narrow) 15.dp else 19.dp,
                vertical = if (narrow) 15.dp else 21.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            color = c.primary,
            fontFamily = AppTheme.font,
            fontSize = (if (narrow) 24 else 28).sp,
            lineHeight = latinLine(if (narrow) 24 else 28),
            fontWeight = W.extra,
            // 只有 bump 进行中才加图层：常驻 graphicsLayer 会让文字走离屏栅格化，AA 与直绘略有差异
            modifier = Modifier.then(
                if (bump.value < 0.999f) {
                    Modifier.graphicsLayer {
                        val s = bump.value
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                } else Modifier,
            ),
        )
        // 钉住行盒：Skia 会把行高按整数像素向上取整，单元格会比网页高 0.5dp
        Text(
            label,
            color = c.variant,
            fontFamily = AppTheme.font,
            fontSize = 13.sp,
            lineHeight = normalLine(13),
            fontWeight = W.semi,
            modifier = Modifier.height(16.dp),
        )
    }
}

/** 设置行：图标徽标 + 名称/副标题 + 尾部控件；行高由内容撑开，底部分割线 */
@Composable
fun SettingRow(
    icon: String,
    name: String,
    sub: String?,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.soft),
                    contentAlignment = Alignment.Center,
                // 网页 .setting-ico 内没有 .material-symbols-rounded 的 font-size 覆盖 → 仍为 24px
                ) { Symbol(icon, 24.dp, c.primary) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 15.sp, lineHeight = normalLine(15), fontWeight = W.bold)
                    if (!sub.isNullOrEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(sub, color = c.variant, fontFamily = AppTheme.font, fontSize = 12.sp, lineHeight = normalLine(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            trailing?.invoke()
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    }
}

/** 圆角小胶囊（音色 chip）：border 始终存在（选中时透明，与网页一致，否则高度差 2px） */
@Composable
fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val bg by animateColorAsState(if (active) c.primary else glassSurface(0), tween(300, easing = CssEase))
    val fg by animateColorAsState(if (active) c.onPrimary else c.variant, tween(300, easing = CssEase))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, tween(300, easing = CssSpring))
    Box(
        Modifier
            .then(if (press < 0.999f) Modifier.scale(press) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (active) Color.Transparent else c.divider, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .miuixRipple(fg)
            // 网页 .tone-chip border-box：1px 边框 + 9/15 内边距 → 内容从 10/16 开始
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, color = fg, fontFamily = AppTheme.font, fontSize = 13.sp, lineHeight = normalLine(13), fontWeight = W.semi) }
}

/** 通用圆角背景容器（输入框等） */
@Composable
fun FieldBox(shape: Shape = RoundedCornerShape(14.dp), modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Box(modifier.clip(shape).background(c.containerHigh).border(1.dp, c.divider, shape), contentAlignment = Alignment.CenterStart) { content() }
}

/** Toast：底部居中的深色气泡 */
@Composable
fun ToastBar(text: String, icon: String) {
    val c = AppTheme.colors
    Row(
        Modifier
            .shadow(16.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(if (c.aurora) c.onSurface else c.onSurface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(icon, 20.dp, if (c.aurora) Color(0xFF12152C) else c.surface)
        Text(text, color = if (c.aurora) Color(0xFF12152C) else c.surface, fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = normalLine(14), fontWeight = W.semi)
    }
}
