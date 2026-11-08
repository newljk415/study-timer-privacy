package com.newljk415.studytimer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newljk415.studytimer.ads.BannerAd
import com.newljk415.studytimer.data.AppSettings
import com.newljk415.studytimer.ui.Section
import com.newljk415.studytimer.domain.TimerStatus
import com.newljk415.studytimer.notify.CompletionFeedback
import com.newljk415.studytimer.notify.Notifications
import com.newljk415.studytimer.ui.MainViewModel
import com.newljk415.studytimer.ui.SettingsScreen
import com.newljk415.studytimer.ui.StatsScreen
import com.newljk415.studytimer.ui.StudyTheme
import com.newljk415.studytimer.ui.TimerScreen

const val PRIVACY_URL = "https://newljk415.github.io/study-timer-privacy/"
const val CONTACT_EMAIL = "morrowclock@gmail.com"

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.Factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            StudyTheme(s.theme) { App(s) }
        }
    }

    @Composable
    private fun App(s: AppSettings) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var confirmExit by rememberSaveable { mutableStateOf(false) }
        var notifAllowed by rememberSaveable { mutableStateOf(Notifications.canPost(this)) }
        val timer by vm.timer.collectAsStateWithLifecycle()
        val days by vm.last7Days.collectAsStateWithLifecycle()
        val running = timer.snapshot?.status == TimerStatus.RUNNING
        val ads = container.ads
        val adsReady by ads.sdkReady.collectAsStateWithLifecycle()
        val privacyRequired by ads.privacyOptionsRequired.collectAsStateWithLifecycle()
        val adsRemoved = s.removeAdsCached
        // 광고 제거 권한이 확인되면 동의 수집·광고 요청을 하지 않는다.
        LaunchedEffect(adsRemoved) { if (!adsRemoved) ads.gatherConsent(this@MainActivity) }
        val showBanner = adsReady && !adsRemoved && ads.configured

        LifecycleResumeEffect(Unit) {
            vm.onResume(); notifAllowed = Notifications.canPost(this@MainActivity)
            onPauseOrDispose { }
        }
        LaunchedEffect(Unit) { vm.completionEvents.collect { CompletionFeedback.play(this@MainActivity, vm.settings.value ?: s) } }
        LaunchedEffect(running, s.keepScreenOn) {
            if (running && s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notifAllowed = Notifications.canPost(this)
        }
        val start = {
            // Android 13+: 처음 시작할 때 한 번만 알림 권한을 요청. 거절해도 타이머는 시작된다.
            if (Build.VERSION.SDK_INT >= 33 && !s.notificationPermissionAsked && !notifAllowed) {
                vm.update { setNotificationAsked() }
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            vm.startOrResume()
        }

        Scaffold(bottomBar = { Column {
            if (showBanner) BannerAd(BuildConfig.BANNER_AD_UNIT_ID)
            NavigationBar {
                listOf("⏱" to "타이머", "📊" to "기록", "⚙" to "설정").forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon, Modifier.clearAndSetSemantics { }) }, label = { Text(label) })
                }
            }
        } }) { pad ->
            val m = Modifier.padding(pad)
            when (tab) {
                0 -> TimerScreen(timer, start, vm::pause, vm::reset, m)
                1 -> StatsScreen(days, m)
                else -> SettingsScreen(
                    settings = s, notificationsAllowed = notifAllowed,
                    onSaveDurations = { f, b, cb -> vm.setDurations(f, b, cb) },
                    onTheme = { t -> vm.update { setTheme(t) } },
                    onKeepScreenOn = { v -> vm.update { setKeepScreenOn(v) } },
                    onSound = { v -> vm.update { setSound(v) } },
                    onVibrate = { v -> vm.update { setVibrate(v) } },
                    onNotificationSettings = ::openNotificationSettings,
                    onPrivacyPolicy = { open(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) },
                    onContact = {
                        open(Intent(Intent.ACTION_SENDTO, "mailto:$CONTACT_EMAIL".toUri())
                            .putExtra(Intent.EXTRA_SUBJECT, "[Study Timer ${BuildConfig.VERSION_NAME}] 문의"))
                    },
                    onExit = { confirmExit = true },
                    versionName = BuildConfig.VERSION_NAME,
                    modifier = m,
                    monetization = {
                        if (!adsRemoved && privacyRequired) {
                            HorizontalDivider(); Section("광고")
                            OutlinedButton(onClick = { ads.showPrivacyOptions(this@MainActivity) }, Modifier.fillMaxWidth()) {
                                Text("광고 개인정보 선택")
                            }
                        }
                    },
                )
            }
        }
        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("앱을 종료할까요?") },
                text = { Text("진행 중인 타이머는 일시정지되고, 지금까지 실제로 집중한 시간은 기록에 저장됩니다. 다음에 앱을 열면 이어서 재개할 수 있습니다. 일시정지된 동안에는 완료 알림이 울리지 않습니다.") },
                confirmButton = { TextButton(onClick = { confirmExit = false; vm.exit { finishAndRemoveTask() } }) { Text("종료") } },
                dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("취소") } },
            )
        }
    }

    private fun open(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNotificationSettings() = open(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    )
}
