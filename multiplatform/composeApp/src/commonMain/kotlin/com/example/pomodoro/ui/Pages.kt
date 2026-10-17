package com.example.pomodoro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pomodoro.AppTheme
import com.example.pomodoro.CssSpring
import com.example.pomodoro.KEY_COLORS
import com.example.pomodoro.LocalCompact
import com.example.pomodoro.LocalPageEntry
import com.example.pomodoro.LocalScreenWidth
import com.example.pomodoro.Page
import com.example.pomodoro.State
import com.example.pomodoro.TONES
import com.example.pomodoro.TimerMode
import com.example.pomodoro.TodoItem
import com.example.pomodoro.W
import com.example.pomodoro.accentColorOrNull
import com.example.pomodoro.normalLine
import com.example.pomodoro.requestNotificationPermission
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private val EaseInOut: Easing = Easing { f -> (1f - cos(f * PI).toFloat()) / 2f }

/* ============================================================
 * 计时页：模式卡片 + 计时卡片
 * ============================================================ */
@Composable
fun TimerPage(state: State, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        CardBox {
            CardTitle("模式", " 选择")
            Spacer(Modifier.height(20.dp))
            Segment(listOf("专注", "短休息", "长休息"), state.mode.ordinal) { i ->
                state.selectMode(TimerMode.entries[i])
            }
        }
        Spacer(Modifier.height(32.dp))
        TimerCard(state)
    }
}

@Composable
private fun TimerCard(state: State) {
    val scope = rememberCoroutineScope()
    val compact = LocalCompact.current
    val screenW = LocalScreenWidth.current
    val narrow = screenW <= 360.dp
    // 网页 .timer-ring：clamp(230px, 62vw, 300px)；@media(max-width:360px) 为 clamp(190, 58vw, 260)
    val ringSize = if (narrow) {
        (screenW * 0.58f).coerceIn(190.dp, 260.dp)
    } else {
        (screenW * 0.62f).coerceIn(230.dp, 300.dp)
    }
    // 网页 .timer-ring { animation: popIn 0.6s var(--spring) }：切换回计时页时重新弹入
    val ringPop = remember { Animatable(1f) }
    LaunchedEffect(state.page) {
        if (state.page == Page.Timer && !State.debugFreezeAnim) {
            ringPop.snapTo(0f)
            ringPop.animateTo(1f, tween(600, easing = CssSpring))
        }
    }
    val entryAnim = LocalPageEntry.current
    // 时钟卡片关掉整块 cardIn：环自己 popIn，控件/圆点按 cardIn 入场（同网页 :not(.timer-ring)）
    CardBox(padding = 0.dp, entry = false) {
        Column(
            Modifier.fillMaxWidth().padding(
                start = if (compact) 20.dp else 24.dp,
                end = if (compact) 20.dp else 24.dp,
                top = if (compact) 20.dp else 34.dp,
                bottom = if (compact) 20.dp else 30.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rp = ringPop.value
            Box(
                if (rp < 0.999f) {
                    Modifier.graphicsLayer {
                        alpha = rp.coerceIn(0f, 1f)
                        val s = 0.9f + 0.1f * rp
                        scaleX = s; scaleY = s
                    }
                } else Modifier,
            ) { TimerRing(state, ringSize) }
            Spacer(Modifier.height(32.dp))
            val cp = entryAnim.card.value
            Column(
                Modifier.then(
                    if (cp < 0.999f) {
                        Modifier.graphicsLayer {
                            val p = entryAnim.card.value
                            alpha = p.coerceIn(0f, 1f)
                            translationY = (1f - p) * 14.dp.toPx()
                            val s = 0.99f + 0.01f * p
                            scaleX = s; scaleY = s
                        }
                    } else Modifier,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // 网页 @media(max-width:360px)：.controls gap 10
                    horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 18.dp),
                ) {
                    GhostButton("replay") { state.reset() }
                    PrimaryButton(
                        text = if (state.running) "暂停" else "开始",
                        icon = if (state.running) "pause" else "play_arrow",
                        running = state.running,
                        onClick = { if (state.running) state.pause() else state.start(scope) },
                    )
                    GhostButton("skip_next") { state.skip() }
                }
                Spacer(Modifier.height(32.dp))
                SessionDots(state)
            }
        }
    }
}

/** 画一段（或整圈）计时环弧线：几何与网页 SVG（viewBox 300、r=130）完全一致 */
private fun DrawScope.drawRingArc(color: Color, sw: Float, p: Float, cap: StrokeCap) {
    val radius = 130f * (size.width / 300f)
    val center = Offset(size.width / 2, size.height / 2)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    drawArc(color, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(sw, cap = cap))
}

@Composable
private fun TimerRing(state: State, size: Dp) {
    val c = AppTheme.colors
    val stroke = (12f * size.value / 300f).dp
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() }

    val inf = rememberInfiniteTransition(label = "ring")
    val ambientAnim by inf.animateFloat(
        0.5f, 0.85f,
        infiniteRepeatable(tween(if (state.running) 2600 else 4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "ambient",
    )
    val ambientScaleAnim by inf.animateFloat(
        0.92f, 1.06f,
        infiniteRepeatable(tween(if (state.running) 2600 else 4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "ambientScale",
    )
    val dotPulseAnim by inf.animateFloat(
        0.6f, 1.3f,
        infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse),
        label = "dotPulse",
    )
    // 截图时冻结到“网页 animation:none”对应的基准态：opacity .55、scale 1（详见 tools/webshot.js）
    val freeze = State.debugFreezeAnim
    val ambient = if (freeze) 0.55f else ambientAnim
    val ambientScale = if (freeze) 1f else ambientScaleAnim
    val dotPulse = if (freeze) 1f else dotPulseAnim

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // 环境光晕：对应 .ring-ambient（inset -14px、circle = 最远角、26% → transparent 70%、opacity .5→.85 呼吸）
        Box(
            Modifier
                .fillMaxSize()
                .scale(ambientScale)
                .background(
                    Brush.radialGradient(
                        0f to c.primary.copy(alpha = 0.26f * ambient),
                        1f to Color.Transparent,
                        center = Offset(px / 2f, px / 2f),
                        radius = px * 0.541f,   // (300+28)/2·√2·0.70 ≈ 162px
                    ),
                ),
        )
        // [data-bg-active]：.ring-track 描边换成 outline 60%
        val track = if (c.bgActive) glassDim(c.outline, 0.60f) else c.containerHighest
        // 网页 .ring-progress { transition: stroke-dashoffset .3s linear }
        val p by animateFloatAsState(state.progress.coerceIn(0f, 1f), tween(300, easing = LinearEasing))
        Canvas(Modifier.fillMaxSize()) {
            drawRingArc(track, stroke.toPx(), 1f, StrokeCap.Butt)
        }
        // 进度弧发光：CSS drop-shadow(0 0 10px primary@60%) → 同弧线以 5dp 高斯模糊刷一遍
        // （CSS 模糊半径 10px ≈ σ5；Android 12 以下 Modifier.blur 为空操作，此时无发光）
        if (p > 0f) {
            Canvas(Modifier.fillMaxSize().blur(13.dp, BlurredEdgeTreatment.Unbounded)) {
                drawRingArc(c.primary.copy(alpha = 0.70f), stroke.toPx(), p, StrokeCap.Round)
            }
            Canvas(Modifier.fillMaxSize()) {
                drawRingArc(c.primary, stroke.toPx(), p, StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(6.dp)
                    .scale(if (state.running) dotPulse else 0.6f)
                    .clip(CircleShape)
                    .background(c.primary.copy(alpha = if (state.running) 1f else 0f)),
            )
            // 网页 .timer-time：clamp(44px, 13vw, 64px)；窄屏按 vw，桌面沿用环尺寸比例
            val timeSize = if (LocalCompact.current) {
                (LocalScreenWidth.current.value * 0.13f).coerceIn(44f, 64f)
            } else {
                (size.value * 0.213f).coerceIn(44f, 64f)
            }
            // CSS line-height:1（行盒 = 字号）：浏览器把负半行距 (fontLine-height)/2 放在盒子上边缘之外，
            // 基线 = ascent - 半行距；Compose 直接把基线放在 ascent 处 —— 差一个半行距，这里手动补回。
            // Inter 度量：(1984+494)/2048 = 1.20996 → 半行距 = 0.10498em
            val halfGap = (1.20996f - 1f) / 2f * timeSize
            Box(Modifier.height(timeSize.dp), contentAlignment = Alignment.Center) {
                Text(
                    state.timeText,
                    color = c.onSurface,
                    fontFamily = AppTheme.font,
                    fontSize = timeSize.sp,
                    lineHeight = timeSize.sp,
                    fontWeight = W.extra,
                    letterSpacing = (-2).sp,
                    modifier = Modifier.offset(y = (-halfGap - 0.5f).dp),
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            }
            Row(
                Modifier.height(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Symbol(state.mode.icon, 18.dp, c.variant)
                Text(state.mode.label, color = c.variant, fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = normalLine(14), fontWeight = W.semi)
            }
        }
    }
}

@Composable
private fun SessionDots(state: State) {
    val c = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(state.sessionsBeforeLong) { i ->
            val done = i < state.cycleDone
            // 圆点本体 10dp；done 的 4px 光晕是 box-shadow，不占布局（否则会把后续圆点推远）
            Box(
                Modifier
                    .size(10.dp)
                    .drawBehind {
                        if (done) drawCircle(c.soft, radius = 9.dp.toPx())
                        drawCircle(if (done) c.primary else c.containerHighest, radius = 5.dp.toPx())
                    },
            )
        }
    }
}

/* ============================================================
 * 统计页
 * ============================================================ */
@Composable
fun StatsPage(state: State) {
    val c = AppTheme.colors
    val today = state.today
    Column(Modifier.fillMaxWidth()) {
        CardBox {
            CardTitle("今日", " 专注")
            Spacer(Modifier.height(20.dp))
            StatGrid(
                listOf(
                    "${today.sessions}" to "完成番茄",
                    "${today.focusMin}" to "专注分钟",
                    "${today.best}" to "最长连续",
                ),
            )
        }
        Spacer(Modifier.height(32.dp))
        CardBox {
            CardTitle("累计", " 专注")
            Spacer(Modifier.height(20.dp))
            StatGrid(
                listOf(
                    "${state.totalSessions}" to "累计番茄",
                    state.totalHours to "累计小时",
                    "${state.activeDays}" to "专注天数",
                ),
            )
            Spacer(Modifier.height(18.dp))
            GhostLabel("download", "导出数据") { state.showToast("数据已导出", "download") }
        }
        Spacer(Modifier.height(32.dp))
        CardBox {
            CardTitle("本周", " 分布")
            Spacer(Modifier.height(20.dp))
            WeekChart(state)
        }
    }
}

/** 网页 .stats-grid：桌面 3 列，窄屏 1 列（间隔 16） */
@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    if (LocalCompact.current) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { (value, label) -> StatCell(value, label, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { (value, label) -> StatCell(value, label, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun GhostLabel(icon: String, text: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            // [data-bg-active]：.btn-ghost 底色 52%
            .background(glassDim(c.containerHigh, 0.52f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Symbol(icon, 20.dp, c.onSurface)
        Text(text, color = c.onSurface, fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = normalLine(14), fontWeight = W.bold)
    }
}

@Composable
private fun WeekChart(state: State) {
    val c = AppTheme.colors
    val week = state.weekStats
    val maxSessions = (week.maxOfOrNull { it.second.sessions } ?: 0).coerceAtLeast(1)
    // 网页 #weekChart：flex 底对齐、高 120、列 gap 8；列 = [计数][柱][星期] gap 6、justify-end
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEachIndexed { idx, (key, stat) ->
            val fraction = stat.sessions.toFloat() / maxSessions
            // 柱：height = max(6, val/max·100)% × 120；宽 18、全圆角 9、180° 渐变、35% 光晕（σ6）
            val barH = (120f * fraction).coerceAtLeast(120f * 0.06f)
            // 网页 barGrow .6s var(--spring)，逐列延迟 .07s（transform-origin: bottom）
            val grow = remember { Animatable(if (State.debugFreezeAnim) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (!State.debugFreezeAnim) grow.animateTo(1f, tween(600, delayMillis = idx * 70, easing = CssSpring))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (stat.sessions > 0) {
                    Text("${stat.sessions}", color = c.primary, fontFamily = AppTheme.font, fontSize = 11.sp, lineHeight = normalLine(11), fontWeight = W.bold)
                    Spacer(Modifier.height(6.dp))
                }
                Box(
                    Modifier.width(18.dp).height(barH.dp).graphicsLayer {
                        scaleY = grow.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
                ) {
                    Box(
                        Modifier.matchParentSize().blur(6.dp, BlurredEdgeTreatment.Unbounded)
                            .background(c.primary.copy(alpha = 0.35f), RoundedCornerShape(9.dp)),
                    )
                    Box(
                        Modifier.matchParentSize()
                            .background(Brush.verticalGradient(listOf(c.primary, c.primaryContainer)), RoundedCornerShape(9.dp)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(weekdayCn(key), color = c.variant, fontFamily = AppTheme.font, fontSize = 11.sp, lineHeight = normalLine(11), fontWeight = W.semi)
            }
        }
    }
}

/** 网页星日标签：["日","一",...][d.getDay()]（周一是 1 → %7） */
private fun weekdayCn(key: String): String {
    val p = key.split("-")
    if (p.size != 3) return ""
    val date = runCatching { LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt()) }.getOrNull() ?: return ""
    return listOf("日", "一", "二", "三", "四", "五", "六")[(date.dayOfWeek.ordinal + 1) % 7]
}

/* ============================================================
 * 待办页
 * ============================================================ */
@Composable
fun TodosPage(state: State) {
    val c = AppTheme.colors
    var input by remember { mutableStateOf("") }
    CardBox {
        CardTitle("待办", " 清单")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp, lineHeight = normalLine(14), fontFamily = AppTheme.font),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    // .bg-input：border-box 高 46（padding 12/14 + 1px 描边 + 20px 行盒）
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(c.containerHigh).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 15.dp, vertical = 13.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // 网页是 <input>：占位符永远单行，不会把输入框撑高
                        if (input.isEmpty()) Text("添加一个待办，回车确认...", color = c.variant, fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = normalLine(14), maxLines = 1, softWrap = false)
                        inner()
                    }
                },
            )
            PrimaryButton(text = "添加", icon = "add", height = 46.dp, fontSize = 14, paddingHorizontal = 18.dp) {
                state.addTodo(input)
                input = ""
            }
        }
        Spacer(Modifier.height(14.dp))
        val doneCount = state.todos.count { it.done }
        if (state.todos.isEmpty()) {
            // 网页空态：.todo-stats 为空且高度 0，上下 margin 折叠 → 只留 14
            Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Text("暂无待办，添加一个开始吧 ✨", color = c.variant, fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = normalLine(14))
            }
        } else {
            Row(Modifier.padding(start = 2.dp)) {
                Text("已完成 $doneCount / ${state.todos.size}", color = c.variant, fontFamily = AppTheme.font, fontSize = 13.sp, lineHeight = normalLine(13), fontWeight = W.semi)
            }
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.todos.forEachIndexed { index, todo ->
                    TodoRow(todo, onToggle = { state.toggleTodo(index) }, onDelete = { state.removeTodo(index) })
                }
            }
        }
    }
}

@Composable
private fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            // [data-bg-active]：.todo-item 底色 52%
            .background(glassDim(c.surface, 0.52f))
            // 网页 .todo-item 有 1px divider 描边（border-box → 内容从 13/15 开始，行高 50）
            .border(1.dp, c.divider, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 网页 .todo-check:active { transform: scale(.85) }，transform .3s var(--spring)
        val checkInteraction = remember { MutableInteractionSource() }
        val checkPressed by checkInteraction.collectIsPressedAsState()
        val checkScale by animateFloatAsState(if (checkPressed) 0.85f else 1f, tween(300, easing = CssSpring))
        Box(
            Modifier
                .size(24.dp)
                .then(if (checkScale < 0.999f) Modifier.scale(checkScale) else Modifier)
                .clip(CircleShape)
                .background(if (todo.done) c.primary else Color.Transparent)
                .border(2.dp, if (todo.done) c.primary else c.outline, CircleShape)
                .clickable(interactionSource = checkInteraction, indication = null, onClick = onToggle)
                .miuixRipple(if (todo.done) c.onPrimary else c.outline),
        )
        Text(
            todo.text,
            Modifier.weight(1f),
            color = if (todo.done) c.variant.copy(alpha = 0.85f) else c.onSurface,
            fontFamily = AppTheme.font,
            fontSize = 15.sp,
            lineHeight = normalLine(15),
            fontWeight = W.semi,
        )
        val delInteraction = remember { MutableInteractionSource() }
        val delPressed by delInteraction.collectIsPressedAsState()
        val delScale by animateFloatAsState(if (delPressed) 0.94f else 1f, tween(300, easing = CssSpring))
        Box(
            Modifier.size(36.dp)
                .then(if (delScale < 0.999f) Modifier.scale(delScale) else Modifier)
                .clip(RoundedCornerShape(12.dp))
                .clickable(interactionSource = delInteraction, indication = null, onClick = onDelete)
                .miuixRipple(c.variant),
            contentAlignment = Alignment.Center,
        ) {
            Symbol("delete", 20.dp, c.variant)
        }
    }
}

/* ============================================================
 * 设置页
 * ============================================================ */
@Composable
fun SettingsPage(state: State) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(32.dp)) {

        // 时长设置
        CardBox {
            CardTitle("时长", " 设置")
            Spacer(Modifier.height(12.dp))
            SettingRow("psychology", "专注时长", "每次深度工作") { Stepper(state.focusMinutes) { state.levelUp("focusMinutes", it) } }
            SettingRow("coffee", "短休息", "小憩一下") { Stepper(state.shortMinutes) { state.levelUp("shortMinutes", it) } }
            SettingRow("self_improvement", "长休息", "深度恢复") { Stepper(state.longMinutes) { state.levelUp("longMinutes", it) } }
            SettingRow("repeat", "长休息间隔", "每几轮进入长休息", showDivider = false) { Stepper(state.sessionsBeforeLong) { state.levelUp("sessionsBeforeLong", it) } }
        }

        // 行为设置
        CardBox {
            CardTitle("行为", " 设置")
            Spacer(Modifier.height(12.dp))
            SettingRow("play_circle", "自动开始下一轮", "结束后自动继续") { AppSwitch(state.autoStart) { state.autoStart = it; state.saveSettings() } }
            SettingRow("notifications", "提示音", "结束时播放提醒") { AppSwitch(state.sound) { state.sound = it; state.saveSettings() } }
            SettingRow("notification_add", "桌面通知", "结束时系统提醒") {
                AppSwitch(state.notify) { want ->
                    if (!want) {
                        state.notify = false
                        state.saveSettings()
                    } else {
                        // 对应网页 requestNotify()：未授权时不要把开关点亮
                        requestNotificationPermission { granted ->
                            state.notify = granted
                            state.saveSettings()
                            state.showToast(
                                if (granted) "桌面通知已开启" else "未授权通知，请在系统设置中允许",
                                if (granted) "notifications" else "error",
                            )
                        }
                    }
                }
            }
            SettingRow("picture_in_picture", "悬浮迷你计时器", "可拖动的迷你圆环") { AppSwitch(state.miniTimer) { state.miniTimer = it; state.saveSettings() } }
            SettingRow("swipe_vertical", "超出回弹", "拖到顶部/底部时的拉伸动画", showDivider = false) {
                AppSwitch(state.overscroll) {
                    state.overscroll = it
                    state.saveSettings()
                    state.showToast(if (it) "已开启超出回弹" else "已关闭超出回弹", "swipe_vertical")
                }
            }
        }

        // 主题风格
        CardBox {
            CardTitle("主题", " 风格")
            Spacer(Modifier.height(18.dp))
            SettingRow("palette", "强调色", "选择你的主色调", showDivider = false) {}
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KEY_COLORS.forEach { (name, hex) ->
                    val active = state.accent.equals(hex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentColorOrNull(hex) ?: c.primary)
                            .clickable { state.accent = hex; state.saveSettings(); state.showToast("已切换到「$name」", "palette") }
                            .miuixRipple(c.onSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) Text("✓", color = Color.White, fontFamily = AppTheme.font, fontSize = 18.sp, fontWeight = W.extra)
                    }
                }
            }
        }

        // 提醒音
        CardBox {
            CardTitle("提醒音", " 声音")
            Spacer(Modifier.height(18.dp))
            SettingRow("play_circle", "开始提醒音", "点击试听，或上传自定义") { UploadBtn { state.showToast("暂不支持上传", "error") } }
            Spacer(Modifier.height(10.dp))
            ToneChips(state.startTone) { state.startTone = it; state.saveSettings(); state.playTone() }
            Spacer(Modifier.height(20.dp))
            SettingRow("notifications_active", "结束提醒音", "点击试听，或上传自定义") { UploadBtn { state.showToast("暂不支持上传", "error") } }
            Spacer(Modifier.height(10.dp))
            ToneChips(state.endTone) { state.endTone = it; state.saveSettings(); state.playTone() }
        }

        // 背景
        CardBox {
            CardTitle("背景", " 图片")
            Spacer(Modifier.height(16.dp))
            Segment(listOf("渐变", "自定义", "API"), when (state.bgMode) { "custom" -> 1; "api" -> 2; else -> 0 }) { i ->
                state.bgMode = listOf("gradient", "custom", "api")[i]
                state.saveSettings()
            }
            Spacer(Modifier.height(16.dp))
            when (state.bgMode) {
                "custom" -> {
                    val scope = rememberCoroutineScope()
                    var url by remember { mutableStateOf(state.bgUrl) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = url,
                            onValueChange = { url = it },
                            singleLine = true,
                            textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp, lineHeight = normalLine(14), fontFamily = AppTheme.font),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.containerHigh).border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (url.isEmpty()) Text("粘贴图片链接 https://...", color = c.variant, fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = normalLine(14))
                                    inner()
                                }
                            },
                        )
                        GhostWideButton(text = if (state.bgLoading) "加载中…" else "应用", icon = "check") {
                            if (!state.bgLoading) scope.launch { state.applyBackgroundUrl(url) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostWideButton("upload", "上传本地图片") { state.showToast("暂不支持本地选图", "error") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("可粘贴图片链接，或从本地选择图片作为背景。", color = c.variant, fontFamily = AppTheme.font, fontSize = 12.sp, lineHeight = 18.sp)
                }
                "api" -> {
                    Text("可选择 API 来源；开启后每隔 5 分钟自动换图。", color = c.variant, fontFamily = AppTheme.font, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostWideButton("shuffle", "换一张") { state.showToast("API 源待接入", "error") }
                    }
                }
                else -> Text("使用主题自带的渐变背景（浅色 / 深色 / 极光）。", color = c.variant, fontFamily = AppTheme.font, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }

        // 关于入口
        CardBox {
            SettingRow("info", "关于", "版本 v1.0.0 (1)", showDivider = false) {
                Box(Modifier.size(28.dp).clickable { state.showAbout = true }, contentAlignment = Alignment.Center) {
                    Symbol("chevron_right", 22.dp, c.variant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun UploadBtn(onClick: () -> Unit) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, tween(300, easing = CssSpring))
    Box(
        Modifier
            .then(if (press < 0.999f) Modifier.scale(press) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            // [data-bg-active]：.upload-btn 底色 52%（默认透明）
            .background(if (c.bgActive) glassDim(c.containerHigh, 0.52f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .miuixRipple(c.primary)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text("上传", color = c.primary, fontFamily = AppTheme.font, fontSize = 13.sp, lineHeight = normalLine(13), fontWeight = W.bold) }
}

@Composable
private fun ToneChips(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TONES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, label) -> Chip(label, active = id == selected) { onSelect(id) } }
            }
        }
    }
}

/* ============================================================
 * 关于页（全屏浮层）
 * ============================================================ */
@Composable
fun AboutPage(state: State, onClose: () -> Unit) {
    val c = AppTheme.colors
    val dark = c.dark
    val fg = if (dark) Color(0xFFECE9FF) else Color(0xFF1B1633)
    val bgColors = if (dark) {
        listOf(Color(0xFF2D1F3D), Color(0xFF241F3E), Color(0xFF181533))
    } else {
        listOf(Color(0xFFFFE3F1), Color(0xFFF0E7FD), Color(0xFFDCD7FA))
    }
    // 网页 .about.show 内的入场：logo aboutPop .5s/.05s、名称 aboutUp .45s/.1s、
    // 卡片 aboutUp .5s/.16s（后续卡片 .22s）；只做位移/缩放，不做 opacity（避免 Backdrop Root）
    val logoPop = remember { Animatable(if (State.debugFreezeAnim) 1f else 0f) }
    val nameUp = remember { Animatable(if (State.debugFreezeAnim) 1f else 0f) }
    val cardUp = remember { Animatable(if (State.debugFreezeAnim) 1f else 0f) }
    val cardUp2 = remember { Animatable(if (State.debugFreezeAnim) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (State.debugFreezeAnim) return@LaunchedEffect
        launch { logoPop.animateTo(1f, tween(500, delayMillis = 50, easing = CssSpring)) }
        launch { nameUp.animateTo(1f, tween(450, delayMillis = 100, easing = CssSpring)) }
        launch { cardUp.animateTo(1f, tween(500, delayMillis = 160, easing = CssSpring)) }
        launch { cardUp2.animateTo(1f, tween(500, delayMillis = 220, easing = CssSpring)) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 网页 .about 的上下内边距含 safe-area-inset-*；背景渐变铺满整屏
        val safe = WindowInsets.systemBars.asPaddingValues()
        val safeTop = safe.calculateTopPadding()
        val safeBottom = safe.calculateBottomPadding()
        // 复刻网页 flex 布局：内容总高固定（卡片 172+348+116 + 间距 48），
        // 主区 min-height 180、bottom padding 12；装不下时 logo（flex-shrink）被压缩
        val cardsTotal = 172f + 348f + 116f + 3f * 16f
        val avail = maxHeight.value - safeTop.value - safeBottom.value - 34f - 44f - cardsTotal
        val mainH = maxOf(180f, avail)
        val logoH = minOf(96f, mainH - 12f - 88f).coerceAtLeast(48f)
        Column(
            Modifier
                .fillMaxSize()
                // 背景：linear-gradient(165deg, …)，42% 中间停靠点（与网页 .about 一致）
                .drawBehind {
                    // 梯度轴方向 (sin165°, -cos165°)≈(0.2588, 0.9659)，长度 = w·|dx| + h·|dy|，过中心
                    val dx = 0.258819f
                    val dy = 0.965926f
                    val len = size.width * dx + size.height * dy
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawRect(
                        Brush.linearGradient(
                            0f to bgColors[0], 0.42f to bgColors[1], 1f to bgColors[2],
                            start = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
                            end = Offset(cx + dx * len / 2f, cy + dy * len / 2f),
                        ),
                    )
                }
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp + safeTop)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Symbol("arrow_back", 24.dp, fg)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(mainH.dp)
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(width = 96.dp, height = logoH.dp).graphicsLayer {
                        val p = logoPop.value
                        val s = 0.62f + 0.38f * p
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                ) {
                    // box-shadow: 0 14px 34px rgba(52,130,255,0.34)（σ=34/2=17px）
                    Canvas(Modifier.fillMaxSize().offset(y = 14.dp).blur(17.dp, BlurredEdgeTreatment.Unbounded)) {
                        drawRoundRect(Color(0x573482FF), cornerRadius = CornerRadius(28.dp.toPx()))
                    }
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0xFF3482FF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 网页 logo 是 SVG 路径：实心圆盘（逆时针）+ 指针多边形（顺时针）→ 非零环绕下指针区域被挖空
                        Canvas(Modifier.size(58.dp)) {
                            val s = size.width / 24f
                            val path = Path().apply {
                                fillType = PathFillType.NonZero
                                val r = Rect(Offset(12f * s, 12f * s), 10f * s)
                                arcTo(r, -90f, -270f, true)
                                arcTo(r, 0f, -90f, false)
                                moveTo(16.2f * s, 16.2f * s)
                                lineTo(11f * s, 13.7f * s)
                                lineTo(11f * s, 7f * s)
                                lineTo(12.5f * s, 7f * s)
                                lineTo(12.5f * s, 12.6f * s)
                                close()
                            }
                            drawPath(path, Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Column(
                    Modifier.graphicsLayer { translationY = (1f - nameUp.value) * 18.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val nameBrush = run {
                        // linear-gradient(100deg, …)：梯度轴过中心，长度 = w·sin+h·cos，58% 中间停靠点
                        val density = LocalDensity.current
                        Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to if (dark) Color(0xFFB9A6FF) else Color(0xFF4A2FB8),
                                0.58f to if (dark) Color(0xFFE0A6FF) else Color(0xFFA24BD0),
                                1f to if (dark) Color(0xFFFF9ECB) else Color(0xFFE0559A),
                            ),
                            start = with(density) { Offset((-2.08f).dp.toPx(), 11.8f.dp.toPx()) },
                            end = with(density) { Offset(96.58f.dp.toPx(), 29.2f.dp.toPx()) },
                        )
                    }
                    Text(
                        "番茄钟",
                        fontFamily = AppTheme.font,
                        fontSize = 32.sp,
                        lineHeight = normalLine(32),
                        fontWeight = W.extra,
                        letterSpacing = (-0.5).sp,
                        style = TextStyle(brush = nameBrush),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("v1.0.0 (1)", color = fg.copy(alpha = 0.5f), fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = W.semi)
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp + safeBottom)) {
                Column(Modifier.graphicsLayer { translationY = (1f - cardUp.value) * 18.dp.toPx() }) {
                    AboutCard(
                        dark,
                        listOf(
                            AboutRow("应用版本", "v1.0.0 (1)", AboutIcon.None),
                            AboutRow("设计参考", "MiuiX", AboutIcon.Chevron),
                            AboutRow("我的 GitHub", "@Simlalsy", AboutIcon.External),
                        ),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.graphicsLayer { translationY = (1f - cardUp2.value) * 18.dp.toPx() }) {
                    AboutCard(
                        dark,
                        listOf(
                            AboutRow("MiuiX for Compose", "compose-miuix-ui/miuix", AboutIcon.External),
                            AboutRow("Material Symbols", "google/material-design-icons", AboutIcon.External),
                            AboutRow("Inter", "rsms/inter", AboutIcon.External),
                            AboutRow("Jetpack Compose", "androidx/androidx", AboutIcon.External),
                            AboutRow("Kotlin", "JetBrains/kotlin", AboutIcon.External),
                            AboutRow("Gradle", "gradle/gradle", AboutIcon.External),
                        ),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.graphicsLayer { translationY = (1f - cardUp2.value) * 18.dp.toPx() }) {
                    AboutCard(
                        dark,
                        listOf(
                            AboutRow("开源许可", "Apache-2.0", AboutIcon.Chevron),
                            AboutRow("第三方许可", "", AboutIcon.Chevron),
                        ),
                    )
                }
            }
        }
    }
}

private enum class AboutIcon { None, Chevron, External }

private data class AboutRow(val label: String, val value: String = "", val icon: AboutIcon = AboutIcon.Chevron)

@Composable
private fun AboutCard(dark: Boolean, rows: List<AboutRow>) {
    val bg = if (dark) Color(0x17FFFFFF) else Color(0xB8FFFFFF)
    val fg = if (dark) Color(0xFFECE9FF) else Color(0xFF1B1633)
    val compact = LocalCompact.current
    // 网页 .about-item 是 flex：标签与地址按各自 max-content 宽度等比收缩。
    // 窄屏用 TextMeasurer 量出两者宽度作为 weight，复刻浏览器逐行的换行位置。
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = AppTheme.font, fontSize = 16.sp, lineHeight = normalLine(16), fontWeight = W.bold)
    val valueStyle = TextStyle(fontFamily = AppTheme.font, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = W.semi)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)) {
        rows.forEach { row ->
            // 行高固定：无图标 36+20=56 / 有图标 36+22=58（网页 .about-item 的实测盒高）
            Row(
                if (compact) {
                    // 窄屏允许标签/地址换行后自然撑高（网页 .about-item 无固定高度）
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (row.icon == AboutIcon.None) 56.dp else 58.dp)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(if (row.icon == AboutIcon.None) 56.dp else 58.dp)
                        .padding(horizontal = 20.dp)
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val labelW = if (compact) {
                    measurer.measure(row.label, labelStyle).size.width.toFloat().coerceAtLeast(1f)
                } else 1f
                val valueW = if (compact && row.value.isNotEmpty()) {
                    measurer.measure(row.value, valueStyle).size.width.toFloat().coerceAtLeast(1f)
                } else 0f
                Text(
                    row.label,
                    color = fg,
                    style = labelStyle,
                    modifier = Modifier.weight(labelW),
                )
                if (row.value.isNotEmpty()) {
                    Text(
                        row.value,
                        color = fg.copy(alpha = 0.55f),
                        style = valueStyle,
                        textAlign = TextAlign.End,
                        modifier = if (compact) Modifier.weight(valueW) else Modifier,
                    )
                }
                when (row.icon) {
                    AboutIcon.None -> {}
                    AboutIcon.Chevron -> Symbol("chevron_right", 22.dp, fg.copy(alpha = 0.42f))
                    AboutIcon.External -> Symbol("open_in_new", 22.dp, fg.copy(alpha = 0.42f))
                }
            }
        }
    }
}
