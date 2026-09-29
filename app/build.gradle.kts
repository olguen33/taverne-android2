plugins { id("com.android.application") }
android {
    namespace = "fr.taverne.mercenaires"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.taverne.mercenaires.updatable"
        minSdk = 26
        targetSdk = 35
        versionCode = 53
        versionName = "0.38.7"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
