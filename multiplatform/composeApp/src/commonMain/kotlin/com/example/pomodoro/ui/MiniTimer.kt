package com.example.pomodoro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pomodoro.AppTheme
import com.example.pomodoro.LocalCompact
import com.example.pomodoro.State
import com.example.pomodoro.W
import kotlin.math.roundToInt

/**
 * 悬浮迷你计时器：对应网页 `.mini-timer`
 * （圆角 40 胶囊 + 52px 迷你进度环 + 18px 等宽时间 + 36px 圆形播放按钮，可拖动）
 */
@Composable
fun MiniTimerOverlay(state: State) {
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxX = with(density) { maxWidth.toPx() }
        val maxY = with(density) { maxHeight.toPx() }
        val pillW = with(density) { 156.dp.toPx() }
        val pillH = with(density) { 68.dp.toPx() }
        val margin = with(density) { 24.dp.toPx() }
        // 网页窄屏 .mini-timer 距底 = 84px + safe-area-inset-bottom（落在底部导航之上）
        val safeBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        val compact = LocalCompact.current
        val bottomGap = if (compact) 84.dp + safeBottom else 24.dp + safeBottom
        val bottomGapPx = with(density) { (bottomGap + 24.dp).toPx() }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = if (compact) 14.dp else 24.dp, bottom = bottomGap)
                .offset {
                    IntOffset(
                        state.miniDx.roundToInt(),
                        state.miniDy.roundToInt(),
                    )
                }
                .pointerInput(compact, safeBottom) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val nx = (state.miniDx + drag.x).coerceIn(-(maxX - pillW - margin * 2), 0f)
                        val ny = (state.miniDy + drag.y).coerceIn(-(maxY - pillH - bottomGapPx), 0f)
                        state.miniDx = nx
                        state.miniDy = ny
                    }
                },
        ) {
            Row(
                Modifier
                    .shadow(16.dp, RoundedCornerShape(40.dp), clip = false, ambientColor = c.shadowLg, spotColor = c.shadowLg)
                    .clip(RoundedCornerShape(40.dp))
                    .background(c.container.copy(alpha = c.container.alpha * 0.80f))
                    .border(1.dp, c.divider, RoundedCornerShape(40.dp))
                    .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 迷你进度环（r=42/100，描边 6/100，与网页 svg 同比例）
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = size.width * 6f / 100f
                        val r = size.width * 42f / 100f
                        val tl = Offset(size.width / 2 - r, size.height / 2 - r)
                        val sz = Size(r * 2, r * 2)
                        drawArc(c.containerHighest, 0f, 360f, false, tl, sz, style = Stroke(sw))
                        val p = state.progress.coerceIn(0f, 1f)
                        if (p > 0f) {
                            drawArc(c.primary, -90f, 360f * p, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                    }
                }
                Text(
                    state.timeText,
                    color = c.onSurface,
                    fontFamily = AppTheme.font,
                    fontSize = 18.sp,
                    fontWeight = W.extra,
                    letterSpacing = (-0.5).sp,
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.primary)
                        .clickable {
                            if (state.running) state.pause() else state.start(scope)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(if (state.running) "pause" else "play_arrow", 20.dp, c.onPrimary)
                }
            }
        }
    }
}
