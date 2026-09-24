plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.falloutlondon.companion"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.falloutlondon.companion"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    sourceSets["main"].res.srcDirs("$buildDir/generated/lockedIcon/res")

    tasks.register<Copy>("copyLockedAppIcon") {
        from(project.file("../../FalloutLondonCompanion/Assets.xcassets/AppIcon.appiconset/AppIcon.png"))
        into(layout.buildDirectory.dir("generated/lockedIcon/res/drawable-nodpi"))
        rename { "app_icon.png" }
    }
    tasks.named("preBuild").configure { dependsOn("copyLockedAppIcon") }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
