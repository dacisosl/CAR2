import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 지도 키는 저장소에 커밋하지 않는다. android/local.properties 또는 환경 변수로 넣는다.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "app.car.parking"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.car.parking"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.4"

        val naverKey = secret("NAVER_MAP_KEY_ID")
        buildConfigField("String", "NAVER_MAP_KEY_ID", "\"$naverKey\"")
        buildConfigField("String", "NAVER_MAP_STYLE_ID", "\"${secret("NAVER_MAP_STYLE_ID")}\"")
    }

    // github: GitHub 릴리스 APK, 앱 안 업데이트 확인 포함
    // play: Google Play 배포용. Play 밖에서 스스로 업데이트하는 기능과 설치 권한을 넣지 않는다
    flavorDimensions += "channel"
    productFlavors {
        create("github") {
            dimension = "channel"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            // 휴대폰(arm64)과 PC 에뮬레이터(x86_64)만 포함해 APK 크기를 줄인다
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        create("play") {
            dimension = "channel"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            // App Bundle은 기기별로 나눠 내려받으므로 32비트 기기까지 포함한다
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
    }

    // 업로드 키는 저장소에 넣지 않는다. android/keystore.properties가 있을 때만 release 서명
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    implementation(libs.naver.map.sdk)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
