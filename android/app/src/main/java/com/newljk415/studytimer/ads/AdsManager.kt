package com.newljk415.studytimer.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.newljk415.studytimer.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UMP 동의 → canRequestAds 확인 → Mobile Ads SDK 1회 초기화.
 * 광고 제거 권한이 있으면 동의 수집·초기화·광고 요청을 하지 않는다.
 */
class AdsManager(context: Context) {
    private val app = context.applicationContext
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(app)
    private val gathering = AtomicBoolean(false)
    private val initStarted = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sdkReady = MutableStateFlow(false)
    /** SDK 초기화 완료 + 현재 동의 상태로 광고 요청 가능. */
    val sdkReady: StateFlow<Boolean> = _sdkReady.asStateFlow()
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** 릴리스 광고 ID가 설정되지 않은 빌드에서는 광고를 요청하지 않는다. */
    val configured: Boolean get() = BuildConfig.BANNER_AD_UNIT_ID.isNotBlank()

    fun gatherConsent(activity: Activity) {
        if (!configured || !gathering.compareAndSet(false, true)) return
        val params = ConsentRequestParameters.Builder().apply {
            // 디버그 동의 테스트 설정은 debug 빌드에만 적용된다(release의 BuildConfig 값은 빈 문자열).
            if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_HASHED_ID.isNotBlank()) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_HASHED_ID)
                        .build(),
                )
            }
        }.build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                if (err != null) Log.w(TAG, "consent form: ${err.errorCode} ${err.message}")
                gathering.set(false)
                onConsentChanged()
            }
        }, { err ->
            Log.w(TAG, "consent update failed: ${err.errorCode} ${err.message}")
            gathering.set(false)
            onConsentChanged()
        })
        // 이전 세션에서 받은 동의가 있으면 갱신을 기다리지 않고 초기화할 수 있다.
        onConsentChanged()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { err ->
            if (err != null) Log.w(TAG, "privacy options: ${err.errorCode} ${err.message}")
            onConsentChanged()
        }
    }

    private fun onConsentChanged() {
        _privacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        val allowed = consent.canRequestAds()
        if (!allowed) { _sdkReady.value = false; return }
        if (initStarted.compareAndSet(false, true)) {
            scope.launch { MobileAds.initialize(app) { _sdkReady.value = consent.canRequestAds() } }
        } else if (initDone) {
            _sdkReady.value = true
        }
    }

    private val initDone get() = MobileAds.getInitializationStatus() != null

    private companion object { const val TAG = "AdsManager" }
}
