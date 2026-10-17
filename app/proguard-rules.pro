# Proguard rules for SafeAlert
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.jetbrains.annotations.* <fields>;
}
