# Proguard rules for SmartRing R99 Bridge
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.health.connect.client.records.* <fields>;
}
