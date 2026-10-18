package com.example.pomodoro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.NotificationCompat
import android.content.SharedPreferences

/**Android SharedPreferences 实现 */
class AndroidSettingsStorage(context: Context) : SettingsStorage {
    private val prefs: SharedPreferences = context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
    override fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun loadInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun loadBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun loadString(key: String, default: String): String = prefs.getString(key, default) ?: default
}

/** 完成通知：与 Activity 解耦，Activity 被回收后仍可发送（用 applicationContext） */
object AndroidNotifier {
    const val CHANNEL_ID = "pomodoro_timer"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "番茄钟通知", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "计时完成通知"
            }
        )
    }

    fun notify(context: Context, title: String, text: String) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        ensureChannel(context)

        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        androidx.core.app.NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var state: State
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cb = permissionCallback
        permissionCallback = null
        cb?.invoke(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        appContext = applicationContext
        enableEdgeToEdge()
        AndroidNotifier.ensureChannel(this)

        val storage = AndroidSettingsStorage(this)
        state = State(storage)
        state.checkDailyReset()
        state.onTimerFinished = {
            state.autoSave()
            TimerService.stop(this)
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            LaunchedEffect(state.running, state.mode) {
                if (state.running) {
                    TimerService.start(this@MainActivity, state.mode.label, "⏱ ${state.mode.label}中...")
                } else {
                    TimerService.stop(this@MainActivity)
                }
            }
            App(state, isDark)
        }
    }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 对应网页 requestNotify()：只在用户打开「桌面通知」开关时请求权限 */
    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        if (hasNotificationPermission()) {
            onResult(true)
            return
        }
        if (permissionCallback != null) {
            onResult(false)
            return
        }
        permissionCallback = onResult
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onPause() {
        super.onPause()
        state.autoSave()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        state.autoSave()
    }

    companion object {
        const val CHANNEL_ID = AndroidNotifier.CHANNEL_ID

        /** 当前 Activity（权限请求需要；通知发送用 [appContext]） */
        @Volatile
        var current: MainActivity? = null
            private set

        /** 应用 Context：Activity 回收后仍能发完成通知 */
        @Volatile
        var appContext: Context? = null
            private set
    }
}
