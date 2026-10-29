plugins { id("com.android.library") }

android {
    namespace = "com.termux.emulator"
    compileSdk = 35
    ndkVersion = "29.0.14206865"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        externalNativeBuild { ndkBuild { cFlags += listOf("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections") } }
    }
    externalNativeBuild { ndkBuild { path = file("src/main/jni/Android.mk") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

// Upstream emulator tests require a Robolectric Android runtime; Codux's JVM test runner
// uses Android's default stub implementation. Keep the upstream source and run Codux tests.
tasks.withType<Test>().configureEach { enabled = false }
