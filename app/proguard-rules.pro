# Preserve launcher classes and enum names in R8 release builds
-keep class com.minimal.launcher.** { *; }
-dontwarn android.app.StatusBarManager
