pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Robolectric 대용량 jar는 공유 IP에서 Maven Central per-IP 한도(429)에 걸리기 쉬워,
        // 테스트 전용 org.robolectric 그룹만 Google이 운영하는 Maven Central 미러(HTTPS)에서 받는다.
        exclusiveContent {
            forRepository {
                maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralGoogleMirror" }
            }
            filter { includeGroup("org.robolectric") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "StudyTimer"
include(":app")
