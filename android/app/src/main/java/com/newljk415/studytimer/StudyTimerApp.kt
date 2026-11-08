package com.newljk415.studytimer

import android.app.Application
import android.content.Context
import com.newljk415.studytimer.data.AndroidTimeSource
import com.newljk415.studytimer.data.RoomTimerStore
import com.newljk415.studytimer.data.SettingsRepository
import com.newljk415.studytimer.data.TimerDatabase
import com.newljk415.studytimer.notify.AlarmCompletionScheduler
import com.newljk415.studytimer.notify.Notifications
import com.newljk415.studytimer.domain.TimerController

/** 수동 DI 컨테이너. 프로세스당 하나. */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val time = AndroidTimeSource(app)
    val db = TimerDatabase.create(app)
    val store = RoomTimerStore(db)
    val settings = SettingsRepository(app)
    val scheduler = AlarmCompletionScheduler(app)
    val controller = TimerController(store, time, { settings.current().durations }, scheduler)
}

class StudyTimerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as StudyTimerApp).container
