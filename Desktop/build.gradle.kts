import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}
group = "com.falloutlondon"
version = file("../VERSION").readText().trim()
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}
kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }
compose.desktop {
    application {
        mainClass = "com.falloutlondon.companion.desktop.MainKt"
        nativeDistributions {
            packageName = "Fallout London Companion"
            packageVersion = version.toString()
            description = "Fallout London Companion desktop client"
            vendor = "Fallout London Companion"
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            windows {
                menuGroup = "Fallout London Companion"
                shortcut = true
                dirChooser = true
                iconFile.set(project.file("src/main/resources/app.ico"))
            }
            linux {
                packageName = "fallout-london-companion"
                appCategory = "Utility"
                iconFile.set(project.file("src/main/resources/app.png"))
            }
        }
    }
}
