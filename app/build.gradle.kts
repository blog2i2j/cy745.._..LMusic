import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp")
    id("android.aop")
}

val keystoreProps = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { Properties().apply { load(FileInputStream(it)) } }

fun releaseTime(pattern: String = "MMdd_HHmm"): String = SimpleDateFormat(pattern).run {
    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    format(Date())
}


androidAopConfig {
    enabled = true
    debug = true

//    include 'com.flyjingfish'
//    cutInfoJson = true
//    increment = true
    // 移除kotlin相关，减少编译错误并提升速度
    exclude(
        "kotlin.jvm",
        "kotlin.internal",
        "kotlinx.coroutines.internal",
        "kotlinx.coroutines.android"
    )
}

android {
    namespace = "com.lalilu"
    compileSdk = libs.versions.compile.version.get().toIntOrNull()

    defaultConfig {
        applicationId = "com.lalilu.lmusic"
        minSdk = libs.versions.min.sdk.version.get().toIntOrNull()
        targetSdk = libs.versions.compile.version.get().toIntOrNull()
        versionCode = 42
        versionName = "1.5.4"

        vectorDrawables {
            useSupportLibrary = true
        }
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    if (keystoreProps != null) {
        val storeFileValue = keystoreProps["storeFile"]?.toString() ?: ""
        val storePasswordValue = keystoreProps["storePassword"]?.toString() ?: ""
        val keyAliasValue = keystoreProps["keyAlias"]?.toString() ?: ""
        val keyPasswordValue = keystoreProps["keyPassword"]?.toString() ?: ""

        if (storeFileValue.isNotBlank() && file(storeFileValue).exists()) {
            signingConfigs.create("release") {
                storeFile(file(storeFileValue))
                storePassword(storePasswordValue)
                keyAlias(keyAliasValue)
                keyPassword(keyPasswordValue)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = kotlin.runCatching { signingConfigs["release"] }.getOrNull()
            resValue("string", "app_name", "@string/app_name_release")
        }

        create("alpha") {
            isMinifyEnabled = true
            isShrinkResources = true

            versionNameSuffix = "-Aplha-${releaseTime()}"
            applicationIdSuffix = ".alpha"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = kotlin.runCatching { signingConfigs["release"] }.getOrNull()
                ?: signingConfigs.getByName("debug")
            resValue("string", "app_name", "@string/app_name_alpha")

            matchingFallbacks.add("release")
            matchingFallbacks.add("debug")

            ndk {
                // mips (已弃用)
                // mips64 (已弃用)
                // armeabi (已弃用)
                // armeabi-v7a (需要支持—现在最流行的处理器架构)
                // arm64-v8a (需要支持—armeabi-v7a的新版本)
                // x86 (可选, 设备非常有限，可以用于模拟器debugging)
                // x86_64 (可选, 设备非常有限，可以用于模拟器debugging)
                // abiFilters.addAll(setOf("armeabi-v7a"))
            }
        }

        create("beta") {
            isMinifyEnabled = true
            isShrinkResources = true

            versionNameSuffix = "-BETA_${releaseTime()}"
            applicationIdSuffix = ".beta"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = kotlin.runCatching { signingConfigs["release"] }.getOrNull()
                ?: signingConfigs.getByName("debug")
            resValue("string", "app_name", "@string/app_name_beta")

            matchingFallbacks.add("release")
            matchingFallbacks.add("debug")
        }

        debug {
            versionNameSuffix = "-DEBUG_${releaseTime("yyyyMMdd")}"
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
            isDebuggable = true
            isJniDebuggable = true

            resValue("string", "app_name", "@string/app_name_debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    lint {
        disable += "Instantiatable"
        abortOnError = false
    }
}

composeCompiler {
    composeCompiler.featureFlags.add(ComposeFeatureFlag.StrongSkipping)
    composeCompiler.featureFlags.add(ComposeFeatureFlag.PausableComposition)
}

dependencies {
    implementation(project(":crash"))
    implementation(project(":component"))
    implementation(project(":lplaylist"))
    implementation(project(":lhistory"))
    implementation(project(":lartist"))
    implementation(project(":lalbum"))
    implementation(project(":lfolder"))
    ksp(libs.koin.compiler)

    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.kotlin.serialization)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)

    implementation(libs.xmlutil.core)
    implementation(libs.xmlutil.serialization)

    // https://github.com/Block-Network/StatusBarApiExample
    // 墨 · 状态栏歌词 API
    implementation("com.github.577fkj:StatusBarApiExample:v2.0")

    // https://gitee.com/simplepeng/SpiderMan
    // Apache-2.0 License
    // 闪退崩溃日志捕获库
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.10")

    // https://github.com/square/retrofit
    // https://mvnrepository.com/artifact/com.squareup.retrofit2/retrofit
    // Apache-2.0 License
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // https://github.com/Commit451/NativeStackBlur
    // Apache-2.0 License
    // Bitmap的Blur实现库
    implementation("com.github.Commit451:NativeStackBlur:1.0.4")

    // https://github.com/qinci/EdgeTranslucent
    // https://github.com/cy745/EdgeTranslucent
    // Undeclared License
    // 实现边沿渐变透明
    // implementation("com.github.cy745:EdgeTranslucent:8c25866a14")

    implementation("com.github.commandiron:WheelPickerCompose:1.1.11")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

//    debugImplementation("com.github.getActivity:Logcat:11.8")
//    debugImplementation("io.github.knight-zxw:blockcanary:0.0.5")
//    debugImplementation("io.github.knight-zxw:blockcanary-ui:0.0.5")
//    debugImplementation("com.github.cy745:wytrace:d0df4c2d15")
//    debugImplementation("com.bytedance.android:shadowhook:1.0.10")
    implementation("io.github.theapache64:rebugger:1.0.0-rc03")

    implementation(libs.bundles.flyjingfish.aop)
    ksp(libs.flyjingfish.aop.ksp)

    implementation("com.google.accompanist:accompanist-adaptive:0.35.1-alpha")
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0-beta04")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0-beta04")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0-beta04")
}