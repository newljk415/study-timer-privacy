plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// ---- 외부 설정(비밀 아님): ~/.gradle/gradle.properties 또는 -P 로 전달. README 참고 ----
fun prop(name: String): String? = providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"       // Google 공식 테스트 앱 ID
val testBannerId = "ca-app-pub-3940256099942544/9214589741"          // Google 공식 테스트 적응형 배너
val releaseAdmobAppId = prop("studytimer.admob.appId")
val releaseBannerId = prop("studytimer.admob.bannerId")
// Play Console > 수익 창출 설정 > 라이선스의 Base64 RSA 공개 키(비밀 아님). 없으면 구매 권한을 부여하지 않는다.
val playLicenseKey = prop("studytimer.play.licenseKey") ?: ""

android {
    namespace = "com.newljk415.studytimer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.newljk415.studytimer"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.4"
        buildConfigField("String", "PLAY_LICENSE_PUBLIC_KEY", "\"$playLicenseKey\"")
    }

    // 배포 서명: 사용자가 별도 보관 중인 기존 키를 외부 속성으로만 연결한다(키·비밀번호는 저장소에 두지 않음).
    // 값이 없으면 release는 서명되지 않은 산출물이 되며, debug는 SDK가 자동 생성한 디버그 키로 서명된다.
    val storeFilePath = prop("studytimer.signing.storeFile")
    if (storeFilePath != null) {
        signingConfigs.create("release") {
            storeFile = file(storeFilePath)
            storePassword = prop("studytimer.signing.storePassword")
            keyAlias = prop("studytimer.signing.keyAlias")
            keyPassword = prop("studytimer.signing.keyPassword")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerId\"")
            buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"${prop("studytimer.ump.testDeviceHashedId") ?: ""}\"")
        }
        release {
            // 값이 없으면 아래 verifyReleaseConfig 가 release 빌드를 중단한다(테스트 ID로 대체하지 않음).
            manifestPlaceholders["admobAppId"] = releaseAdmobAppId ?: "MISSING"
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"${releaseBannerId ?: ""}\"")
            buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 런타임이 받는 android-all jar도 같은 미러 사용(TLS 유지)
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
            // API 37 런타임(ApplicationSharedMemory)이 JDK 21에서 FileDescriptor 내부(SharedSecrets)에 접근하도록 허용(테스트 JVM 한정)
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Compose ui-test가 끌어오는 Espresso 3.5.0은 API 37에서 제거된 InputManager.getInstance()를 호출함
    testImplementation(libs.espresso.core)
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.compose.ui.test.manifest)
}

val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    description = "release 빌드에 필요한 실서비스 설정 값이 있는지 확인한다."
    val missing = buildList {
        if (releaseAdmobAppId == null || releaseAdmobAppId.contains("3940256099942544")) add("studytimer.admob.appId")
        if (releaseBannerId == null || releaseBannerId.contains("3940256099942544")) add("studytimer.admob.bannerId")
        if (playLicenseKey.isEmpty()) add("studytimer.play.licenseKey")
    }
    doLast {
        if (missing.isNotEmpty()) throw GradleException(
            "release 설정 누락/테스트 값: ${missing.joinToString()} — android/README.md 'Release 설정' 참고",
        )
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfig) }
