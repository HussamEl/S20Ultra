# kotlinx.serialization ships its own consumer rules; ML Kit and Play Services too.
# Keep the serializable route model explicitly as an extra safety net.
-keep,includedescriptorclasses class se.eldebosh.nastastopp.route.model.** { *; }
-keepclassmembers class se.eldebosh.nastastopp.route.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# Strip debug/verbose logging calls from release builds (belt and braces; logs are also
# guarded by BuildConfig.DEBUG in code).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
