# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.syll.**$$serializer { *; }
-keepclassmembers class com.syll.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Rive runtime (JNI).
-keep class app.rive.runtime.** { *; }
