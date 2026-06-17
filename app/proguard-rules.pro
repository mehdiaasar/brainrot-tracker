# Strip verbose logging from release builds (warnings and errors are kept)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# kotlinx-serialization: keep generated serializers for the NavKey data objects
# (Navigation 3 serializes them for state save/restore)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.example.brainrottracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.brainrottracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.brainrottracker.**$$serializer { *; }
