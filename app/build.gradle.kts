import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Kunci Supabase per server dibaca dari secrets.properties (tidak ikut git).
// Lihat secrets.properties.example.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = (secrets.getProperty(key) ?: System.getenv(key) ?: "").trim()
fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Merek tampilan, sama dengan NEXT_PUBLIC_BRAND di PWA (generus/src/lib/brand.ts):
//   kliik   (bawaan) — KLIIK JM: navy #1A2189 + cyan #008CD7, logo dari klien.
//   generus           — desain lama (hijau tua + kuningan, logo masjid & bintang).
// Kembali ke desain lama: ./gradlew assembleResmiRelease -Pbrand=generus (atau generus.brand di gradle.properties).
val brand = ((findProperty("brand") ?: findProperty("generus.brand"))?.toString() ?: "kliik").trim().lowercase()
require(brand == "kliik" || brand == "generus") { "brand harus kliik atau generus, bukan '$brand'" }
val appName = if (brand == "kliik") "KLIIK JM" else "Generus"

android {
    namespace = "id.generus.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.generus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "BRAND", quoted(brand))
        buildConfigField("String", "APP_TIMEZONE", quoted(secret("APP_TIMEZONE").ifEmpty { "Asia/Jakarta" }))
    }

    // Satu kode, dua server (docs/DEPLOY.md di proyek PWA):
    // resmi = Tahap 1 (generus & presensi), uji = semua modul.
    flavorDimensions += "server"
    productFlavors {
        create("uji") {
            dimension = "server"
            applicationIdSuffix = ".uji"
            versionNameSuffix = "-uji"
            resValue("string", "app_name", "$appName Uji")
            buildConfigField("String", "SUPABASE_URL", quoted(secret("UJI_SUPABASE_URL")))
            buildConfigField("String", "SUPABASE_ANON_KEY", quoted(secret("UJI_SUPABASE_ANON_KEY")))
            buildConfigField("boolean", "FEATURE_KURIKULUM", "true")
        }
        create("resmi") {
            dimension = "server"
            resValue("string", "app_name", appName)
            buildConfigField("String", "SUPABASE_URL", quoted(secret("RESMI_SUPABASE_URL")))
            buildConfigField("String", "SUPABASE_ANON_KEY", quoted(secret("RESMI_SUPABASE_ANON_KEY")))
            buildConfigField("boolean", "FEATURE_KURIKULUM", secret("RESMI_FEATURE_KURIKULUM").ifEmpty { "false" })
        }
    }

    // Ikon peluncur & warna jendela per merek (src/brand/<merek>/res).
    sourceSets["main"].res.srcDir("src/brand/$brand/res")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Tanpa keystore rilis, APK rilis ditandatangani kunci debug agar tetap bisa dipasang untuk uji.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    // Ktor ≥ 3.2.4: 3.2.0 bawaan supabase-kt gagal di-dex untuk minSdk < 30.
    implementation(platform("io.ktor:ktor-bom:${libs.versions.ktor.get()}"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
