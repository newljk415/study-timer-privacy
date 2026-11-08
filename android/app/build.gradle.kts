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

android {
    namespace = "com.newljk415.studytimer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.newljk415.studytimer"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    description = "release 빌드에 필요한 실서비스 설정 값이 있는지 확인한다."
    val missing = buildList {
        if (releaseAdmobAppId == null || releaseAdmobAppId.contains("3940256099942544")) add("studytimer.admob.appId")
        if (releaseBannerId == null || releaseBannerId.contains("3940256099942544")) add("studytimer.admob.bannerId")
    }
    doLast {
        if (missing.isNotEmpty()) throw GradleException(
            "release 설정 누락/테스트 값: ${missing.joinToString()} — android/README.md 'Release 설정' 참고",
        )
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfig) }
