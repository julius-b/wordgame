plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinParcelize) apply false
}

// https://issuetracker.google.com/issues/240445963?pli=1
// https://github.com/GoogleContainerTools/jib/blob/master/build.gradle#L31
buildscript {
    dependencies {
        classpath("org.apache.commons:commons-compress:1.26.0")
    }
}
