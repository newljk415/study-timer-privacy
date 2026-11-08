# Study Timer — Android 앱 (재구현, v1.0.4)

Kotlin + Jetpack Compose 한국어 공부 타이머. 이 디렉터리(`android/`)가 Android 프로젝트이며,
저장소 루트의 `index.html`(GitHub Pages 개인정보처리방침)은 건드리지 않습니다.

| 항목 | 값 |
|---|---|
| applicationId | `com.newljk415.studytimer` |
| 기존 배포 | versionCode 4 / versionName 1.0.3 |
| 이번 재구현 | **versionCode 5 / versionName 1.0.4** |
| 개인정보처리방침 | https://newljk415.github.io/study-timer-privacy/ |
| 문의 | morrowclock@gmail.com |

## 1. 도구·라이브러리 버전

| 구성 | 버전 | 비고 |
|---|---|---|
| JDK | 21 (Gradle 실행) | 바이트코드 타깃 Java 17 |
| Gradle (Wrapper) | 9.6.1 | AGP 9.5 최소 요구 9.6.0 |
| Android Gradle Plugin | 9.5.1 | 내장 Kotlin 지원 사용 |
| Kotlin / Compose 컴파일러 플러그인 | 2.4.21 | |
| KSP | 2.3.13 | Room 코드 생성 |
| Compose BOM | 2026.09.00 | Material 3 |
| AndroidX core / activity / lifecycle | 1.19.1 / 1.13.0 / 2.11.0 | |
| Room / DataStore | 2.8.5 / 1.3.0 | 기록·타이머 상태 / 설정 |
| Google Mobile Ads / UMP | 25.5.0 / 4.0.0 | |
| Play Billing Library | 9.1.0 | |
| 테스트 | JUnit 4.13.2, kotlinx-coroutines-test 1.11.0, Robolectric 4.17 | |

SDK 요구사항
- `compileSdk = 37` (`platforms;android-37.0`): Compose 1.12/AndroidX core 1.19가 API 37 이상으로 컴파일할 것을 요구합니다.
- `targetSdk = 37`: Google Play 최소 요건(“신규 앱과 앱 업데이트는 Android 16(API 36) 이상을 타깃해야 함”, 2026-11 확인:
  https://support.google.com/googleplay/android-developer/answer/11926878)을 충족하는 최신 API. API 37 동작 변경은 실기기(Android 17)에서 확인이 필요합니다.
  Robolectric UI 테스트는 `@Config(sdk = [35])` 런타임으로 실행합니다.
- `minSdk = 26`, build-tools 36.0.0(AGP가 자동 설치), platform-tools.

```bash
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
```

`local.properties`는 각 개발 환경에서만 만들고 커밋하지 않습니다(`.gitignore` 처리).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### 공급망 무결성
- `gradle-wrapper.properties`에 `distributionSha256Sum`(공식 `gradle-9.6.1-bin.zip.sha256`)을 고정해 배포 파일을 검증합니다.
- `gradle/wrapper/gradle-wrapper.jar`는 Gradle 9.6.1이 생성했으며 SHA-256
  `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`이 공식 `gradle-9.6.1-wrapper.jar.sha256`과 일치함을 확인했습니다. 이 jar는 반드시 추적합니다.
- 의존성은 Google Maven, Maven Central(HTTPS)에서만 받습니다. TLS·체크섬 검증은 끄지 않았습니다.
- 예외: 테스트 전용 `org.robolectric` 그룹은 공유 IP에서 Maven Central의 per-IP 다운로드 한도(HTTP 429)에 걸려
  Google이 운영하는 Maven Central 미러(`maven-central.storage-download.googleapis.com`)에서 받습니다(내용 동일, SHA-1 대조 확인).

## 2. 빌드·테스트 (비밀 값 불필요)

```bash
cd android
```

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew lintDebug
```

debug 빌드는 Android SDK가 자동 생성한 **디버그 키**(`~/.android/debug.keystore`)로 서명됩니다. 이 키는 기기 설치·테스트용일 뿐이며,
Play에 배포된 기존 앱을 업데이트할 수 없습니다.

## 3. Release 설정 (외부 값, 저장소에 커밋 금지)

`~/.gradle/gradle.properties`(개인 환경) 또는 `-P`로 전달합니다. 모두 비밀이 아닌 식별자/공개 키이며, 서명 비밀번호만 비밀입니다.

| 속성 | 내용 | 필수 |
|---|---|---|
| `studytimer.admob.appId` | AdMob 실서비스 앱 ID (`ca-app-pub-…~…`) | release |
| `studytimer.admob.bannerId` | AdMob 실서비스 배너 광고 단위 ID (`ca-app-pub-…/…`) | release |
| `studytimer.play.licenseKey` | Play Console › 수익 창출 설정 › 라이선스의 Base64 RSA **공개** 키 | release |
| `studytimer.signing.storeFile` 외 `storePassword`, `keyAlias`, `keyPassword` | 사용자가 보관 중인 **기존** 업로드/서명 키 | 배포 시 |
| `studytimer.ump.testDeviceHashedId` | UMP 디버그 테스트 기기 해시 ID | 선택(debug 전용) |

- release 빌드(`preReleaseBuild`)는 위 3개 값이 없거나 Google 테스트 ID(`3940256099942544`)이면 `verifyReleaseConfig`에서 **실패**합니다.
  테스트 ID를 실서비스 값으로 조용히 대체하지 않습니다.
- 서명 속성이 없으면 release 산출물은 서명되지 않습니다. 이 프로젝트는 새 서명 키를 만들지 않습니다.
- **기존 사용자에게 1.0.4를 업데이트로 배포하려면 기존 키가 필요합니다.** Play App Signing을 사용 중이면 기존 업로드 키로 서명한 AAB를 올리면 되고,
  업로드 키를 잃었다면 Play Console에서 업로드 키 재설정을 요청해야 합니다(앱 서명 키 자체는 Google이 보관). Play App Signing 미사용 앱은 원래 서명 키가 없으면 업데이트할 수 없습니다.

```bash
./gradlew bundleRelease
```

## 4. 타이머 동작 정책

- 기본 집중 25분 / 휴식 5분. 범위: 집중 1~180분, 휴식 1~60분(범위 밖이면 저장하지 않음).
- 시작 → 일시정지 → 재개, 초기화는 확인 대화상자 후 실행. 완료 후 다음 세션(집중→휴식→집중)은 **사용자가 직접 시작**합니다.
- 설정 변경: **대기(IDLE) 세션과 이후 세션에만** 적용. 진행 중·일시정지 세션은 시작 시 길이를 유지합니다.
- 남은 시간은 UI의 1초 감소가 아니라 Room에 저장된 상태(누적 실행 시간 + 현재 구간 기준 시각)와 현재 시각으로 계산합니다.

### 시계의 역할과 복구
| 상황 | 사용 시계 | 정책 |
|---|---|---|
| 같은 부팅 안(백그라운드, 화면 꺼짐, 프로세스 종료 후 재실행) | 단조 시계 `elapsedRealtime` | 시스템 시각 변경의 영향 없음 |
| 재부팅 후(`Settings.Global.BOOT_COUNT` 변경) | 벽시계 | 구간 시작 벽시계 기준으로 복구. 음수는 0, 세션 길이를 넘으면 잘라냄 |
| 날짜 귀속 | 벽시계 + 실제 경과 | 구간 시작 벽시계부터 단조 경과만큼을 지역 날짜 경계로 분리 |

재부팅 중 사용자가 시계를 크게 바꾸면 그 구간의 복구 오차를 피할 수 없으며, 이 경우에도 세션 길이를 초과하거나 음수가 되지 않도록 제한합니다.

### 기록(원자성·멱등성)
- 모든 상태 전이는 하나의 Room 트랜잭션에서 `상태 읽기 → 기록 반영 → 상태 저장`으로 수행됩니다.
- 완료 처리는 저장된 세션 ID와 상태가 일치할 때 한 번만 일어나고, 즉시 새 세션 ID로 바뀝니다. 화면 재생성·재실행·알림 수신·재부팅 복원이 겹쳐도 중복 기록되지 않습니다.
- 앱이 꺼진 동안 끝난 세션은 복원 시 한 번만 완료 처리하며, 종료 시각 이후 시간은 누적하지 않습니다.
- 실제 집중 시간(일시정지 제외)은 실행 구간이 닫힐 때(일시정지·초기화·종료·완료) 반영합니다. 초기화한 세션의 실제 집중 시간은 기록되고 완료 횟수에는 포함되지 않습니다.
- **완료 횟수 날짜 귀속**: 세션이 끝난 시점의 지역 날짜. **시간대 변경**: 반영 시점의 기기 시간대로 날짜를 계산하며, 이미 저장된 기록은 다시 계산하지 않습니다.
- 휴식 시간은 집중 시간에 포함되지 않습니다. 최근 7일(오늘 포함) 기록을 보여 줍니다.

### 종료 버튼
설정 › “앱 종료”: 확인 후 진행 중 타이머를 **일시정지**하고 지금까지의 실제 집중 시간을 기록한 뒤 `finishAndRemoveTask()`로 화면을 닫습니다.
다음 실행 때 이어서 재개할 수 있고, 일시정지 중에는 완료 알림이 예약되지 않습니다. 프로세스 강제 종료는 사용하지 않습니다.

### 완료 알림 vs 타이머 복원
- **타이머 복원·기록은 정확**합니다(저장 상태 기반, 알림과 무관).
- **시스템 알림 시각은 정확히 보장하지 않습니다.** `AlarmManager.setAndAllowWhileIdle`(정확한 알람 권한 없음, 상시 포그라운드 서비스 없음)을 사용하므로
  Doze 상태에서는 몇 분 늦을 수 있습니다. 앱을 열면 즉시 정확한 상태가 반영됩니다.
- Android 13+에서는 첫 시작 시 한 번 알림 권한을 요청합니다. 거절해도 타이머·기록은 정상 동작합니다.
- 재부팅 시 `BOOT_COMPLETED`로 실행 중 세션을 복원하고 알림을 다시 예약합니다.
- 소리·진동은 앱 설정을 따르며 알림 채널 자체는 무음입니다.

## 5. 광고(AdMob + UMP)
- 앱 시작 시 UMP `requestConsentInfoUpdate` → `loadAndShowConsentFormIfRequired` → `canRequestAds()`가 참일 때만 `MobileAds.initialize`(1회, AtomicBoolean) 후 배너를 요청합니다.
- debug: 공식 테스트 앱 ID `ca-app-pub-3940256099942544~3347511713`, 테스트 배너 `ca-app-pub-3940256099942544/9214589741`. UMP 디버그 지역/테스트 기기는 debug에서만 적용됩니다.
- 설정 › “광고 개인정보 선택”은 UMP가 개인정보 옵션을 요구하는 지역에서 표시됩니다.
- 배너 `AdView`는 Compose에서 한 번 생성·로드하고 `resume/pause/destroy`를 생명주기에 연결합니다.
- 광고 제거 권한이 확인되면 배너를 제거하고 동의 수집·새 광고 요청을 하지 않습니다.

## 6. 결제(Google Play Billing, 일회성 상품)
- 상품 ID `remove_ads`, 구매 옵션 ID `remove-ads-once`. `ProductDetails.getOneTimePurchaseOfferDetailsList()`에서 해당 구매 옵션의
  기본가(offerId 없음) 오퍼를 우선 선택해 `offerToken`으로 결제를 실행하고, 현지화 가격(`formattedPrice`)을 버튼에 표시합니다.
- 권한 정책
  - `PENDING`: 권한 부여·승인 안 함(대기 안내).
  - `PURCHASED`: 검증 성공 시 권한 부여(DataStore 캐시) 후 미승인이면 `acknowledgePurchase`. 실패하면 다음 재조회 때 재시도.
  - 재조회 시점: 앱 실행·복귀(onResume), 구매 콜백 직후, 자동 재연결(`enableAutoServiceReconnection`), “구매 복원” 버튼.
  - 재조회 **성공** + 유효 구매 없음 → 환불·취소로 보고 권한 해제.
  - 재조회 **실패**(오프라인, 서비스 연결 등) → 캐시된 권한 유지. 일시적 실패를 소유권 상실로 취급하지 않습니다.
  - 처리 전체를 Mutex로 직렬화해 중복 콜백·동시 재조회에도 승인/권한 갱신이 안전합니다.
- 검증(서버 없음): Play 라이선스 공개 키로 `originalJson`의 SHA1withRSA 서명을 실제 검증하고, 상품 ID·패키지명·상태·토큰 일치를 확인합니다.
  키가 설정되지 않은 빌드는 권한을 부여하지 않습니다. **한계**: 변조된 기기/앱에서는 클라이언트 검증을 우회할 수 있습니다.
  서버 검증(Play Developer API)은 서비스 계정 키가 필요하므로 앱에 넣지 않았습니다.
- 로컬 단위 테스트는 가짜 게이트웨이와 실제 RSA 키로 정책을 검증할 뿐, **실제 Play 결제 성공을 의미하지 않습니다.**

## 7. Play Console / AdMob에서 필요한 설정
1. Play Console: 일회성 상품 `remove_ads` 생성, 구매 옵션 `remove-ads-once`(구매 유형) 활성화, 가격 설정.
2. Play Console › 수익 창출 설정 › 라이선스 공개 키를 `studytimer.play.licenseKey`로 설정.
3. 기존 서명/업로드 키로 서명한 1.0.4(versionCode 5) AAB를 내부 테스트 트랙에 업로드.
4. 라이선스 테스터 계정 등록(테스트 카드: 승인/거절/지연 결제로 PENDING 확인).
5. AdMob: 앱(`com.newljk415.studytimer`) 등록, 배너 광고 단위 생성 → `studytimer.admob.appId`, `studytimer.admob.bannerId`.
6. AdMob › 개인정보 및 메시지: GDPR(및 필요한 지역) 동의 메시지 게시. `app-ads.txt` 설정 권장.
7. Play Console 데이터 보안 양식이 개인정보처리방침과 일치하는지 확인(광고 ID, 구매 내역, 진단 정보 등).

## 8. 실기기 검증 절차(미실행 — 아래 참고)
1. debug 설치: 테스트 배너 표시, UMP(테스트 기기 해시 + EEA) 동의 양식 → 동의 후에만 배너, “광고 개인정보 선택” 재열기.
2. 타이머: 25분 기본, 시작/일시정지/재개/초기화 확인, 화면 회전·큰 글씨(설정 › 글꼴 크기 최대)·작은 화면에서 잘림 없음.
3. 백그라운드·화면 꺼짐 후 복귀, 최근 앱에서 스와이프 종료 후 재실행, `adb shell am kill` 후 재실행 → 남은 시간·기록 일치.
4. 실행 중 재부팅 → 복원/완료 1회, 알림 재예약. 시스템 시각 변경 → 남은 시간 불변.
5. 23:50에 시작해 자정을 넘겨 날짜별 분리 확인. 알림 권한 거절 상태에서도 타이머·기록 동작.
6. 내부 테스트 트랙 + 라이선스 테스터로 구매/지연 결제(PENDING)/취소/환불(Play Console에서 환불 후 재실행 시 권한 해제)/오프라인 재실행(권한 유지)/재설치 후 구매 복원.

## 9. 개인정보처리방침과의 일치
| 방침 내용 | 구현 |
|---|---|
| 설정·타이머 상태·날짜별 집중 시간·완료 수·구매 권한·알림 안내 여부를 기기에만 저장 | DataStore + Room(앱 전용 저장소), 서버 전송 없음 |
| 자동 백업·기기 전송 미사용 | `allowBackup=false`, `fullBackupContent=false`, `data_extraction_rules`에서 cloud-backup/device-transfer 전부 제외 |
| AdMob 배너, UMP 동의 후 광고 요청, 설정의 ‘광고 개인정보 선택’ | 구현 |
| 광고 제거 확인 시 배너 제거·새 광고 요청 중단 | 구현 |
| 구매 토큰·서명 데이터로 검증·승인, 실행·복귀·복원 시 재조회 | 구현 |
| 인터넷·네트워크 상태, Android 13+ 알림 선택, 진동, 화면 켜두기, 재부팅 후 복원 | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED` |
| 연락처·사진·마이크·카메라·정밀 위치 미요청 | 해당 권한 없음 |

라이브러리가 병합하는 권한(debug merged manifest 확인):
- Google Mobile Ads: `com.google.android.gms.permission.AD_ID`, `ACCESS_ADSERVICES_AD_ID/ATTRIBUTION/TOPICS`, 내부 WorkManager용 `WAKE_LOCK`, `FOREGROUND_SERVICE`
  → 방침의 “기기·광고 식별자” 처리 설명과 일치. 앱 자체는 포그라운드 서비스를 선언·실행하지 않습니다.
- Billing: `com.android.vending.BILLING`(Play 구매).
- `REORDER_TASKS`는 debug 전용 Compose 테스트 매니페스트에서만 들어오며 release에는 포함되지 않습니다.

방침의 “재부팅 후 타이머 복원”은 `RECEIVE_BOOT_COMPLETED` 수신기로 구현되어 있습니다. 앱 동작과 현재 방침 사이에 수정이 필요한 불일치는 발견하지 못했습니다.
