# NOTE: minification is currently disabled for release (see app/build.gradle.kts and
# DECISIONS.md). These rules are kept so that R8 can be re-enabled safely later.

# ML Kit / Firebase components are created by reflection from manifest meta-data. R8 full mode
# otherwise strips their no-arg constructors and text recognition fails at runtime.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-keep class com.google.android.datatransport.** { *; }

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
