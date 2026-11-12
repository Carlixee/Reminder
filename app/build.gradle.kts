import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The official signature: it's OUTSIDE the project, in the FIRMA_Reminder_NON_CANCELLARE folder
// next to CalendarioLezioni (so it never ends up on GitHub by mistake).
// If it's missing (another PC), it still builds with Android Studio's debug signature.
val cartellaFirma = rootProject.file("../FIRMA_Reminder_NON_CANCELLARE")
val datiFirma = Properties().apply {
    val file = File(cartellaFirma, "firma.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    // The "namespace" is the name of the code folders (com/ac/reminder).
    // What counts for the phone is applicationId, below.
    namespace = "com.ac.reminder"
    compileSdk = 36

    defaultConfig {
        // The app's identity card: decided on 8/11, it never changes again
        applicationId = "com.ac.reminder"
        minSdk = 26          // Android 8 and up
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        if (datiFirma.getProperty("password") != null) {
            create("ufficiale") {
                storeFile = File(cartellaFirma, datiFirma.getProperty("file"))
                storePassword = datiFirma.getProperty("password")
                keyAlias = datiFirma.getProperty("alias")
                keyPassword = datiFirma.getProperty("password")
            }
        }
    }

    buildTypes {
        // Android Studio's ▶ uses the official signature too: so it always updates
        // the same app, without having to uninstall
        val ufficiale = signingConfigs.findByName("ufficiale")
        debug {
            if (ufficiale != null) signingConfig = ufficiale
        }
        release {
            isMinifyEnabled = false
            if (ufficiale != null) signingConfig = ufficiale
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Only for the tests on the PC
    testImplementation("junit:junit:4.13.2")
}
