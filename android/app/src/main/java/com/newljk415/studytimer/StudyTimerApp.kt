package com.newljk415.studytimer

import android.app.Application
import android.content.Context
import com.newljk415.studytimer.ads.AdsManager
import com.newljk415.studytimer.billing.EntitlementCache
import com.newljk415.studytimer.billing.EntitlementManager
import com.newljk415.studytimer.billing.PlayBillingGateway
import com.newljk415.studytimer.billing.PurchaseVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    val ads = AdsManager(app)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val billingGateway by lazy { PlayBillingGateway(app) }
    val entitlements by lazy {
        EntitlementManager(
            billingGateway,
            PurchaseVerifier(BuildConfig.PLAY_LICENSE_PUBLIC_KEY, app.packageName),
            object : EntitlementCache {
                override suspend fun get() = settings.current().removeAdsCached
                override suspend fun set(value: Boolean) { settings.setRemoveAdsCached(value) }
            },
        ).also { m ->
            appScope.launch { m.loadCache() }
            appScope.launch { billingGateway.updates.collect { u ->
                m.onUpdate(u)
                // 콜백 후 항상 전체 재조회로 상태를 확정(이미 소유·중복 콜백 포함)
                m.refresh()
            } }
        }
    }
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
