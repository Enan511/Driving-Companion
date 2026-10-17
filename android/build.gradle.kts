plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("verifyToolchain") {
    group = "verification"
    description = "Checks the JDK and SDK required for the first real feature; does not build an app."
    doLast {
        check(JavaVersion.current() == JavaVersion.VERSION_21) {
            "Use project-local JDK 21 through scripts/gradle.ps1."
        }
        val sdkRoot = providers.environmentVariable("ANDROID_HOME").orNull
            ?: error("ANDROID_HOME is unset. Use scripts/gradle.ps1.")
        check(file("$sdkRoot/platforms/android-36/android.jar").isFile) {
            "Android SDK platform 36 is missing. Run scripts/bootstrap.ps1."
        }
        check(file("$sdkRoot/build-tools/35.0.0/aapt2.exe").isFile) {
            "Android build tools 35.0.0 are missing."
        }
        logger.lifecycle("JDK 21 and Android SDK platform 36 / build tools 35.0.0 verified.")
    }
}

tasks.named("check") { dependsOn("verifyToolchain", ":core:drive:test", ":app:testDebugUnitTest", ":app:lintDebug") }
