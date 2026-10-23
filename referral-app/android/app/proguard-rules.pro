# kotlinx.serialization: keep generated serializers for @Serializable models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.referral.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.referral.app.**$$serializer { *; }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**

# pdfbox-android (resume text). Optional JPEG2000 decoder isn't bundled; keep the font/encoding tables it loads by name.
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

# kotlinx.serialization for library models too (supabase-kt, navigation routes). Harmless if duplicated.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }
-dontwarn java.lang.management.**

# Credential Manager (Google sign-in): the Play services provider is found by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
