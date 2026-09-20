import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // O plugin do compilador Compose e obrigatorio a partir do Kotlin 2.0.
    alias(libs.plugins.compose.compiler)
}

// Credenciais de assinatura do APK de release. Ficam em keystore.properties, na
// raiz do projeto e FORA do controle de versao (veja .gitignore). Sem esse arquivo
// o build continua funcionando, mas o release sai sem assinatura.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.arkz.armodelviewer"

    // O SceneView 4.38.0 exige compileSdk 37 (as bibliotecas sao compiladas
    // contra essa plataforma e o AGP valida isso via AAR metadata).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arkz.armodelviewer"
        // 24 e o minSdk minimo suportado pelo SceneView/ARCore.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        // 1.0.0 = primeiro release publico (tag v1.0.0 no GitHub).
        versionName = "1.0.0"
    }

    signingConfigs {
        // So e criada quando keystore.properties existe: assim o repositorio
        // publico compila (sem assinatura) em qualquer maquina.
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ---------- AndroidX / Compose ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    // ---------- Realidade Aumentada ----------
    // arsceneview traz o SceneView (Filament) + integracao ARCore e ja inclui
    // o modulo "sceneview" (3D puro) de forma transitiva.
    implementation(libs.sceneview.arsceneview)

    // ARCore: usado diretamente para o AugmentedImageDatabase, para as classes
    // AugmentedImage/Config/Session e para o ArCoreApk (disponibilidade).
    implementation(libs.google.arcore)
}
