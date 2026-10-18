// Plugin versions are declared once here and applied without a version in
// app/build.gradle.kts. These are current as of September 2026 per the
// framework v7.0 addendum (Sections 59, 71) - verified via web search where
// it mattered (AGP, Play target-SDK policy, Hilt's minSdk floor), not
// assumed from training data. If Android Studio's sync / Upgrade Assistant
// suggests newer patch versions on first open, accepting those suggestions
// is expected and fine - this environment has no Android SDK or network
// path to Google's Maven repository, so none of this has been compiled
// here (see README "What hasn't been verified").
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.google.devtools.ksp") version "2.2.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.60" apply false
}
