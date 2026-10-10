# Spotify App Remote SDK (uses Gson + reflection on its protocol types)
-keep class com.spotify.** { *; }
-dontwarn com.spotify.**
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# kotlinx.serialization (generated serializers for our DTOs / nav routes)
-keepclassmembers class com.spectra.player.** {
    *** Companion;
}
-keepclasseswithmembers class com.spectra.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.spectra.player.**$$serializer { *; }

# Retrofit / OkHttp ship their own consumer rules; silence optional deps.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
