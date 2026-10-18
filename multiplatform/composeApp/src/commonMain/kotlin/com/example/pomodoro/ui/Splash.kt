package com.example.pomodoro.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pomodoro.AppTheme
import com.example.pomodoro.CssEase
import com.example.pomodoro.W

/**
 * 启动加载页：对应网页 `#loading`
 *
 * - 72×72 / 圆角 20 的 logo（soft 底 + 主色图标），`breathe` 2.2s 呼吸（scale 1 → 1.08）
 * - 标题 20px / 700 / 字距 0.3
 * - 两条 200×4 进度条（App / Font），条内填充主色、圆角 2，状态行 13px 且等宽数字
 */
@Composable
fun SplashScreen(appProgress: Float, fontProgress: Float, alpha: Float = 1f) {
    val c = AppTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(c.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val inf = rememberInfiniteTransition(label = "breathe")
            // 网页 @keyframes breathe 2.2s var(--ease)：1 → 1.08 → 1
            val breathe by inf.animateFloat(
                1f, 1.08f,
                infiniteRepeatable(tween(1100, easing = CssEase), RepeatMode.Reverse),
                label = "breatheScale",
            )
            Box(
                Modifier
                    .size(72.dp)
                    .scale(breathe)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.soft),
                contentAlignment = Alignment.Center,
            ) { Symbol("timer", 40.dp, c.primary) }

            Text("番茄钟", color = c.onSurface, fontFamily = AppTheme.font, fontSize = 20.sp, fontWeight = W.bold, letterSpacing = 0.3.sp)

            LoadingRow("App", appProgress)
            LoadingRow("Font", fontProgress)
        }
    }
}

@Composable
private fun LoadingRow(label: String, progress: Float) {
    val c = AppTheme.colors
    Column(Modifier.width(200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.containerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.primary),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.variant, fontFamily = AppTheme.font, fontSize = 13.sp)
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = c.variant, fontFamily = AppTheme.font, fontSize = 13.sp)
        }
    }
}
