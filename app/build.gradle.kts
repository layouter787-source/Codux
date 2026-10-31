plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

// Variante Google Play: CODUX_PLAY=1 faz o app mirar o API 35 (exigência da loja).
// O Android 10+ não deixa apps que miram o API 29+ executar arquivos de filesDir, então nessa
// variante o runtime Termux roda tudo via /system/bin/linker64 (termux-exec system_linker_exec)
// e uma pequena biblioteca nativa (libcodux-selfexe.so) corrige /proc/self/exe para o Codex.
// Sem CODUX_PLAY o app continua mirando o API 28 e executa os binários diretamente.
val playBuild = System.getenv("CODUX_PLAY") == "1"

android { namespace = "dev.codux"; compileSdk = 35
    defaultConfig {
        applicationId = "dev.codux"; minSdk = 26
        targetSdk = if (playBuild) 35 else 28
        // O CI define a versao; localmente cai nos valores padrao.
        versionCode = System.getenv("CODUX_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("CODUX_VERSION_NAME") ?: "0.1.0"
        // O bootstrap Termux e so aarch64; a Play exige 64 bits.
        if (playBuild) { ndk { abiFilters += "arm64-v8a" } }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { disable += "ExpiredTargetSdkVersion" }

    if (playBuild) {
        ndkVersion = "29.0.14206865"
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
        // A biblioteca de LD_PRELOAD precisa existir como arquivo em nativeLibraryDir.
        packaging { jniLibs { useLegacyPackaging = true } }
        lint { abortOnError = false }
    }

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
