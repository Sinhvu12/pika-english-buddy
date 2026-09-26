plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.pika.englishbuddy"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.pika.englishbuddy"; minSdk = 30; targetSdk = 35
  versionCode = 6; versionName = "6.0-simple-friend"
  buildConfigField("String", "PIKA_TOKEN_URL", "\"https://example.invalid/token\"")
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildFeatures { buildConfig = true }
}
kotlin { jvmToolchain(17) }
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("com.google.android.material:material:1.12.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
}