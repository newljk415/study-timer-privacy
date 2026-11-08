package com.newljk415.studytimer.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.newljk415.studytimer.MainActivity
import com.newljk415.studytimer.R
import com.newljk415.studytimer.container
import com.newljk415.studytimer.data.AppSettings
import com.newljk415.studytimer.domain.CompletionEvent
import com.newljk415.studytimer.domain.CompletionScheduler
import com.newljk415.studytimer.domain.SessionType
import com.newljk415.studytimer.domain.TimerSnapshot
import com.newljk415.studytimer.domain.TimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 완료 알림 예약. 정확한 알람 권한(SCHEDULE_EXACT_ALARM)과 상시 포그라운드 서비스를 쓰지 않는다.
 * setAndAllowWhileIdle은 절전(Doze) 중에도 발생하지만 시스템이 몇 분 늦출 수 있다.
 * 타이머 자체의 정확도는 알림과 무관하게 저장된 기준 시각으로 보장된다.
 */
class AlarmCompletionScheduler(private val context: Context) : CompletionScheduler {
    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, TimerAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun sync(snapshot: TimerSnapshot, remainingMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (snapshot.status == TimerStatus.RUNNING && remainingMs > 0) {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + remainingMs, pending(),
            )
        } else {
            am.cancel(pending())
        }
    }
}

object Notifications {
    const val CHANNEL = "completion"
    private const val ID = 1001

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "세션 완료", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "집중·휴식 세션이 끝났을 때 알립니다. 소리·진동은 앱 설정을 따릅니다."
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun showCompleted(context: Context, ev: CompletionEvent) {
        if (!canPost(context)) return // 거절해도 타이머·기록은 정상 동작
        val (title, text) = if (ev.type == SessionType.FOCUS) "집중 세션 완료" to "준비되면 휴식을 시작하세요."
        else "휴식 완료" to "준비되면 다음 집중을 시작하세요."
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try { NotificationManagerCompat.from(context).notify(ID, n) } catch (_: SecurityException) { }
    }
}

object CompletionFeedback {
    fun play(context: Context, settings: AppSettings) {
        if (settings.sound) runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
        if (settings.vibrate) runCatching {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.refreshAsync(context: Context, notify: Boolean) {
    val result = goAsync()
    val c = context.container
    receiverScope.launch {
        try {
            // 같은 세션 ID의 완료는 트랜잭션 안에서 한 번만 처리된다(UI·복원과 경합해도 안전).
            val ev = c.controller.refresh()
            if (ev != null && notify) {
                Notifications.showCompleted(context, ev)
                CompletionFeedback.play(context, c.settings.current())
            }
        } finally {
            result.finish()
        }
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = refreshAsync(context, notify = true)
}

/** 재부팅 후 알람이 사라지므로, 실행 중이던 세션을 복원(필요 시 완료 처리)하고 알림을 다시 예약한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) refreshAsync(context, notify = true)
    }
}
