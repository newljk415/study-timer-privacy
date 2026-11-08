package com.newljk415.studytimer.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.newljk415.studytimer.domain.TimeSource
import java.time.ZoneId

class AndroidTimeSource(private val context: Context) : TimeSource {
    override fun wallMillis() = System.currentTimeMillis()
    override fun elapsedMillis() = SystemClock.elapsedRealtime()
    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun bootId(): String {
        val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        // BOOT_COUNT가 없으면 부팅 시각(10분 단위)으로 근사한다.
        return if (count >= 0) "c$count" else "t" + ((wallMillis() - elapsedMillis()) / 600_000L)
    }
}
