plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace = "dev.codux"; compileSdk = 35
    // targetSdk 28 e intencional: a partir do API 29, apps que miram o API 29+ nao podem executar
    // binarios gravados em filesDir (SELinux/W^X). O runtime Termux embutido (bash, codex) vive em
    // filesDir/usr, entao e preciso mirar o API 28, como o proprio app Termux faz.
    defaultConfig {
        applicationId = "dev.codux"; minSdk = 26; targetSdk = 28
        // O CI define a versao; localmente cai nos valores padrao.
        versionCode = System.getenv("CODUX_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("CODUX_VERSION_NAME") ?: "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { disable += "ExpiredTargetSdkVersion" }

    // Assinatura de release: so existe quando o CI fornece o keystore por variaveis de ambiente.
    // Nada de senha ou keystore fica no repositorio.
    val releaseKeystore = System.getenv("CODUX_KEYSTORE_FILE")
    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("CODUX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CODUX_KEY_ALIAS")
                keyPassword = System.getenv("CODUX_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (!releaseKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom); androidTestImplementation(bom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(project(":termux-terminal-view"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
