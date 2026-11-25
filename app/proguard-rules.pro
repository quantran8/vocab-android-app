# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.vocabflow.**$$serializer { *; }
-keepclassmembers class com.vocabflow.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Rive runtime (JNI).
-keep class app.rive.runtime.** { *; }
