package com.example.pomodoro

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/* ---------- 跨平台时间工具（kotlinx-datetime） ---------- */

internal fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal fun formatHm(ts: Long): String {
    val dt = Instant.fromEpochMilliseconds(ts).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${pad2(dt.hour)}:${pad2(dt.minute)}"
}

/** 与网页 todayKey() 完全一致：`Y-M-D`（月份/日期不补零） */
internal fun todayKey(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.year}-${d.monthNumber}-${d.dayOfMonth}"
}

/** 最近 n 天的 key（含今天，最早的在前），用于「本周分布」 */
internal fun recentDayKeys(n: Int = 7): List<String> {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    return (n - 1 downTo 0).map { back ->
        val day = today.minus(kotlinx.datetime.DatePeriod(days = back))
        "${day.year}-${day.monthNumber}-${day.dayOfMonth}"
    }
}

internal fun todayStartMillis(): Long {
    val tz = TimeZone.currentSystemDefault()
    val date = Clock.System.now().toLocalDateTime(tz).date
    return date.atStartOfDayIn(tz).toEpochMilliseconds()
}

/* ---------- 持久化接口（平台层实现） ---------- */

interface SettingsStorage {
    fun saveInt(key: String, value: Int)
    fun loadInt(key: String, default: Int): Int
    fun saveBoolean(key: String, value: Boolean)
    fun loadBoolean(key: String, default: Boolean): Boolean
    fun saveString(key: String, value: String)
    fun loadString(key: String, default: String): String
}

object NoOpStorage : SettingsStorage {
    override fun saveInt(key: String, value: Int) {}
    override fun loadInt(key: String, default: Int): Int = default
    override fun saveBoolean(key: String, value: Boolean) {}
    override fun loadBoolean(key: String, default: Boolean): Boolean = default
    override fun saveString(key: String, value: String) {}
    override fun loadString(key: String, default: String): String = default
}

/* ---------- 数据模型（对应网页 MODES / dayStats / todos） ---------- */

enum class TimerMode(val id: String, val label: String, val icon: String) {
    Focus("focus", "专注", "psychology"),
    Short("short", "短休息", "coffee"),
    Long("long", "长休息", "self_improvement");

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: Focus
    }
}

/** 网页 MODES[*].color：专注 = primary，短休息 = green，长休息 = cyan */
fun modeAccent(c: AppColors, mode: TimerMode): Color = when (mode) {
    TimerMode.Focus -> c.primary
    TimerMode.Short -> c.short
    TimerMode.Long -> c.long
}

@Immutable
data class DayStat(val sessions: Int = 0, val focusMin: Int = 0, val best: Int = 0)

@Immutable
data class TodoItem(val text: String, val done: Boolean = false)

@Immutable
data class ToastMsg(val text: String, val icon: String = "check")

/** 导航项（对应 .rail-item，顺序与网页一致） */
enum class Page(val id: String, val label: String, val icon: String, val title: String, val subtitle: String) {
    Timer("timer", "计时", "timer", "番茄钟", "专注每一刻"),
    Stats("stats", "统计", "monitoring", "统计", "你的专注足迹"),
    Todos("todos", "待办", "checklist", "待办", "记录你的任务"),
    Settings("settings", "设置", "settings", "设置", "自定义你的节奏"),
}

const val TONE_CUSTOM = "custom"

@Stable
class State(private val storage: SettingsStorage = NoOpStorage) {

    /* ================= 设置（对应网页 DEFAULTS） ================= */
    var focusMinutes by mutableIntStateOf(storage.loadInt("focusMinutes", 25))
    var shortMinutes by mutableIntStateOf(storage.loadInt("shortMinutes", 5))
    var longMinutes by mutableIntStateOf(storage.loadInt("longMinutes", 15))
    var sessionsBeforeLong by mutableIntStateOf(storage.loadInt("sessionsBeforeLong", 4))
    var autoStart by mutableStateOf(storage.loadBoolean("autoStart", false))
    var sound by mutableStateOf(storage.loadBoolean("sound", true))
    var notify by mutableStateOf(storage.loadBoolean("notify", false))
    var miniTimer by mutableStateOf(storage.loadBoolean("miniTimer", false))
    var overscroll by mutableStateOf(storage.loadBoolean("overscroll", true))
    var theme by mutableStateOf(storage.loadString("theme", "system"))
    var accent by mutableStateOf(storage.loadString("accent", "#3482FF"))
    var bgMode by mutableStateOf(storage.loadString("bgMode", "gradient"))
    /** 自定义背景图链接（对应网页 settings.bgUrl） */
    var bgUrl by mutableStateOf(storage.loadString("bgUrl", "")); private set
    /** 已加载完成的背景图（对应网页 #bgImage 的 backgroundImage）；null = 无背景图 */
    var bgImage by mutableStateOf<ImageBitmap?>(null); private set
    /** 正在下载/解码背景图（设置页按钮显示「加载中」） */
    var bgLoading by mutableStateOf(false); private set
    /** 背景图是否生效 —— 卡片据此切换毛玻璃（对应网页 html[data-bg-active]） */
    val bgActive: Boolean get() = bgMode != "gradient" && bgImage != null
    /** 上次成功加载的 URL，避免刷新时重复下载 */
    private var loadedBgUrl: String? = null
    var startTone by mutableStateOf(storage.loadString("startTone", "chime"))
    var endTone by mutableStateOf(storage.loadString("endTone", "chime"))

    /* ================= 计时（对应网页 mode/remaining/total/running/endAt） ================= */
    var mode by mutableStateOf(TimerMode.Focus); private set
    var running by mutableStateOf(false); private set
    var secondsLeft by mutableIntStateOf(focusMinutes * 60); private set
    var totalSeconds by mutableIntStateOf(focusMinutes * 60); private set
    var finished by mutableStateOf(false); private set
    private var endAt = 0L
    private var timerJob: Job? = null

    /** 平台钩子：完成一轮时调用（安卓版用来同步前台服务/通知） */
    var onTimerFinished: (() -> Unit)? = null

    /* ================= 周期与统计 ================= */
    var completedSessions by mutableIntStateOf(storage.loadInt("cycle", 0)); private set
    private val days = mutableStateMapOf<String, DayStat>()
    val today: DayStat get() = days[todayKey()] ?: DayStat()

    /* ================= 待办 ================= */
    val todos = mutableStateListOf<TodoItem>()

    /* ================= 界面状态 ================= */
    var page by mutableStateOf(Page.Timer)
    var showAbout by mutableStateOf(false)

    /** 关于页里的许可/设计参考弹窗 key（对应网页 .about-modal 的 miuix/license/third） */
    var aboutModal by mutableStateOf<String?>(null)
    var toast by mutableStateOf<ToastMsg?>(null)
    var taskName by mutableStateOf(storage.loadString("taskName", ""))

    /** 悬浮迷你计时器的拖动偏移（px）。对应网页 .mini-timer 的拖拽位置（clamp 在视口内） */
    var miniDx by mutableStateOf(0f)
    var miniDy by mutableStateOf(0f)

    /** 迷你计时器是否应当显示（对应网页 updateMiniTimer：开关打开且本轮已开始） */
    val miniVisible: Boolean
        get() = miniTimer && (running || secondsLeft < totalSeconds)

    init {
        loadDays()
        loadTodos()
    }

    /* ================= 派生值 ================= */
    /**
     * 进度环的比例。网页里 `frac = remaining / total`，即**初始为满圈、随时间逐渐变空**
     * （updateRing(): strokeDashoffset = C * (1 - frac)）。
     */
    val progress: Float
        get() = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds else 0f

    val timeText: String get() = "${pad2(secondsLeft / 60)}:${pad2(secondsLeft % 60)}"

    val modeMinutes: Int
        get() = when (mode) {
            TimerMode.Focus -> focusMinutes
            TimerMode.Short -> shortMinutes
            TimerMode.Long -> longMinutes
        }

    /** 当前长休息周期内已完成的专注轮数（圆点用，与网页 updateDots 的 doneCount 一致） */
    val cycleDone: Int
        get() = if (completedSessions > 0 && completedSessions % sessionsBeforeLong == 0) sessionsBeforeLong
        else completedSessions % sessionsBeforeLong

    val untilLong: Int get() = sessionsBeforeLong - cycleDone

    val weekStats: List<Pair<String, DayStat>>
        get() = recentDayKeys(7).map { it to (days[it] ?: DayStat()) }

    val totalSessions: Int get() = days.values.sumOf { it.sessions }
    val totalFocusMin: Int get() = days.values.sumOf { it.focusMin }
    /** 累计小时：网页 (totalFocus / 60).toFixed(1)，始终保留一位小数 */
    val totalHours: String get() {
        val tenths = (totalFocusMin * 10 + 30) / 60
        return "${tenths / 10}.${tenths % 10}"
    }
    val activeDays: Int get() = days.count { it.value.sessions > 0 }

    /* ================= 计时行为 ================= */
    private fun secondsOf(m: TimerMode) = when (m) {
        TimerMode.Focus -> focusMinutes
        TimerMode.Short -> shortMinutes
        TimerMode.Long -> longMinutes
    } * 60

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true
        finished = false
        endAt = nowMillis() + secondsLeft * 1000L
        if (startTone.isNotEmpty()) playTone()
        timerJob?.cancel()
        timerJob = scope.launch { tickLoop() }
    }

    private suspend fun tickLoop() {
        while (running) {
            delay(100)
            val left = ((endAt - nowMillis()) / 1000.0)
            val s = if (left <= 0) 0 else kotlin.math.ceil(left).toInt()
            if (s != secondsLeft) secondsLeft = s
            if (s <= 0) {
                running = false
                complete()
                return
            }
        }
    }

    fun pause() {
        running = false
        timerJob?.cancel(); timerJob = null
    }

    fun reset() {
        pause()
        finished = false
        totalSeconds = secondsOf(mode)
        secondsLeft = totalSeconds
    }

    fun skip() {
        pause()
        complete(skipped = true)
    }

    fun selectMode(m: TimerMode) {
        pause()
        mode = m
        finished = false
        totalSeconds = secondsOf(m)
        secondsLeft = totalSeconds
        page = Page.Timer
    }

    /** 一轮结束（或跳过）后的流转：与网页 handleComplete 一致 */
    private fun complete(skipped: Boolean = false) {
        finished = true
        if (endTone.isNotEmpty()) playTone()
        val todayK = todayKey()
        if (mode == TimerMode.Focus) {
            completedSessions += 1
            val stat = days[todayK] ?: DayStat()
            val sessions = stat.sessions + 1
            days[todayK] = stat.copy(
                sessions = sessions,
                focusMin = stat.focusMin + focusMinutes,
                best = maxOf(stat.best, completedSessions.coerceAtMost(sessionsBeforeLong)),
            )
            saveDays()
            saveSettings()
        }
        if (notify && !skipped) {
            platformNotify(
                "🍅 番茄钟",
                if (mode == TimerMode.Focus) "专注完成！休息一下吧 ☕" else "休息结束，准备开始新的专注 🎯",
            )
        }
        onTimerFinished?.invoke()
        val next = when (mode) {
            TimerMode.Focus -> if (completedSessions > 0 && completedSessions % sessionsBeforeLong == 0) TimerMode.Long else TimerMode.Short
            else -> TimerMode.Focus
        }
        mode = next
        totalSeconds = secondsOf(next)
        secondsLeft = totalSeconds
        if (autoStart && !skipped) {
            running = false
            // 交给界面下一帧启动（需要 CoroutineScope）
            pendingAutoStart = true
        } else {
            running = false
        }
    }

    /** complete() 里不能直接起协程，交给界面消费 */
    var pendingAutoStart by mutableStateOf(false); private set

    fun consumeAutoStart(scope: CoroutineScope) {
        if (!pendingAutoStart) return
        pendingAutoStart = false
        start(scope)
    }

    /* ================= 设置读写 ================= */
    fun levelUp(key: String, delta: Int): Int {
        val (cur, min, max) = when (key) {
            "focusMinutes" -> Triple(focusMinutes, 1, 90)
            "shortMinutes" -> Triple(shortMinutes, 1, 30)
            "longMinutes" -> Triple(longMinutes, 1, 60)
            else -> Triple(sessionsBeforeLong, 2, 8)
        }
        val next = (cur + delta).coerceIn(min, max)
        when (key) {
            "focusMinutes" -> focusMinutes = next
            "shortMinutes" -> shortMinutes = next
            "longMinutes" -> longMinutes = next
            else -> sessionsBeforeLong = next
        }
        if (!running && (mode == TimerMode.Focus && key == "focusMinutes" ||
                mode == TimerMode.Short && key == "shortMinutes" ||
                mode == TimerMode.Long && key == "longMinutes")) {
            totalSeconds = secondsOf(mode)
            secondsLeft = totalSeconds
        }
        saveSettings()
        return next
    }

    fun cycleTheme(systemDark: Boolean) {
        theme = when (theme) {
            "system" -> if (systemDark) "light" else "dark"
            "light" -> "dark"
            "dark" -> "aurora"
            else -> "light"
        }
        saveSettings()
        val name = when (theme) {
            "system" -> "跟随系统"
            "light" -> "浅色"
            "dark" -> "深色"
            else -> "极光"
        }
        showToast("主题：$name", "palette")
    }

    fun effectiveDark(systemDark: Boolean): Boolean = when (theme) {
        "light" -> false
        "dark", "aurora" -> true
        else -> systemDark
    }

    fun addTodo(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        todos.add(TodoItem(t))
        saveTodos()
    }

    fun toggleTodo(index: Int) {
        todos[index] = todos[index].let { it.copy(done = !it.done) }
        saveTodos()
    }

    fun removeTodo(index: Int) {
        todos.removeAt(index)
        saveTodos()
    }

    fun setTask(s: String) {
        taskName = s
        storage.saveString("taskName", s)
    }

    /**
     * 加载当前设置对应的背景图（对应网页 applyBackground()）。
     * 由界面在启动、以及 bgUrl/bgMode 变化时调用；失败会移除背景并提示。
     */
    suspend fun refreshBackgroundImage() {
        val url = if (bgMode == "custom") bgUrl else ""
        if (url.isEmpty()) {
            bgImage = null
            loadedBgUrl = null
            bgLoading = false
            return
        }
        if (url == loadedBgUrl && bgImage != null) return
        bgLoading = true
        val img = loadImageBitmap(url)
        bgLoading = false
        if (img != null) {
            bgImage = img
            loadedBgUrl = url
        } else {
            bgImage = null
            loadedBgUrl = null
            showToast("背景图片加载失败", "error")
        }
    }

    /**
     * 应用自定义背景图：先校验链接并确认真的能下载，成功才持久化
     * （对应网页 bgApplyBtn：testImageUrl 通过后才 saveSettings）。
     */
    suspend fun applyBackgroundUrl(url: String) {
        val u = url.trim()
        if (u.isEmpty()) {
            showToast("请输入图片链接", "error")
            return
        }
        if (!isSupportedImageUrl(u)) {
            showToast("请输入以 http(s):// 开头的图片链接", "error")
            return
        }
        bgLoading = true
        val img = loadImageBitmap(u)
        bgLoading = false
        if (img == null) {
            showToast("图片加载失败，请检查链接", "error")
            return
        }
        bgUrl = u
        bgMode = "custom"
        bgImage = img
        loadedBgUrl = u
        saveSettings()
        showToast("背景已更新", "image")
    }

    fun clearBackground() {
        bgUrl = ""
        bgImage = null
        loadedBgUrl = null
        bgMode = "gradient"
        saveSettings()
        showToast("已恢复渐变背景", "palette")
    }

    fun showToast(text: String, icon: String = "check") {
        toast = ToastMsg(text, icon)
    }

    fun playTone() {
        if (sound) platformPlaySound()
    }

    fun saveSettings() {
        storage.saveInt("focusMinutes", focusMinutes)
        storage.saveInt("shortMinutes", shortMinutes)
        storage.saveInt("longMinutes", longMinutes)
        storage.saveInt("sessionsBeforeLong", sessionsBeforeLong)
        storage.saveBoolean("autoStart", autoStart)
        storage.saveBoolean("sound", sound)
        storage.saveBoolean("notify", notify)
        storage.saveBoolean("miniTimer", miniTimer)
        storage.saveBoolean("overscroll", overscroll)
        storage.saveString("theme", theme)
        storage.saveString("accent", accent)
        storage.saveString("bgMode", bgMode)
        storage.saveString("bgUrl", bgUrl)
        storage.saveString("startTone", startTone)
        storage.saveString("endTone", endTone)
        storage.saveInt("cycle", completedSessions)
    }

    /** 立即把所有数据写入平台存储（安卓在 onPause/onStop 与计时完成时调用） */
    fun autoSave() {
        saveSettings()
        saveDays()
        saveTodos()
    }

    /** 仅供截图/演示：构造一个指定进度的计时状态（不写存储、不触发完成逻辑） */
    fun debugTimerState(remaining: Int, running: Boolean = false, cycle: Int = 0) {
        secondsLeft = remaining
        this.running = running
        completedSessions = cycle
    }

    companion object {
        /** 截图/像素比对时置 true：环形呼吸等动画停在基准相位，与网页冻结后的静态帧可比 */
        var debugFreezeAnim = false
    }

    /** 跨天：把统计切到新的一天（对应网页 ensureToday） */
    fun checkDailyReset() {
        val k = todayKey()
        if (k != lastDay) {
            lastDay = k
            completedSessions = 0
            saveSettings()
        }
    }

    private var lastDay = todayKey()

    /* ================= 序列化（storage 只有 String/Int/Bool） ================= */
    private fun loadDays() {
        val raw = storage.loadString("days", "")
        raw.split(";").filter { it.isNotBlank() }.forEach { entry ->
            val p = entry.split(",")
            if (p.size == 4) {
                val s = p[1].toIntOrNull() ?: 0
                val f = p[2].toIntOrNull() ?: 0
                val b = p[3].toIntOrNull() ?: 0
                days[p[0]] = DayStat(s, f, b)
            }
        }
    }

    private fun saveDays() {
        storage.saveString("days", days.entries.joinToString(";") { "${it.key},${it.value.sessions},${it.value.focusMin},${it.value.best}" })
    }

    private fun loadTodos() {
        val raw = storage.loadString("todos", "")
        raw.split("\u0001").filter { it.isNotBlank() }.forEach { item ->
            val done = item.startsWith("1:")
            todos.add(TodoItem(item.substring(2), done))
        }
    }

    private fun saveTodos() {
        storage.saveString("todos", todos.joinToString("\u0001") { (if (it.done) "1:" else "0:") + it.text })
    }
}

/* ---------- 音色（对应网页 TONES） ---------- */
val TONES: List<Pair<String, String>> = listOf(
    "chime" to "风铃",
    "bell" to "铃声",
    "marimba" to "马林巴",
    "wood" to "木鱼",
    "beep" to "提示音",
    "custom" to "自定义",
    "none" to "无声",
)
