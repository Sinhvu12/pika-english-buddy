plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.pika.englishbuddy"; compileSdk = 35
 defaultConfig {
  applicationId = "com.pika.englishbuddy"; minSdk = 30; targetSdk = 35
  versionCode = 8; versionName = "8.0-pika-3d-ui"
  val tokenUrl = (project.findProperty("PIKA_TOKEN_URL") as String?) ?: "https://example.invalid/token"
  buildConfigField("String", "PIKA_TOKEN_URL", "\"$tokenUrl\"")
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