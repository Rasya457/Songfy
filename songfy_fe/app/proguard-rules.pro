# =============================================================
# Songfy ProGuard / R8 Rules
# =============================================================

# ---- Debugging: preserve line numbers in crash stack traces ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Kotlin ----
-keepclassmembers class **$Companion { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ---- kotlinx.serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$serializer {
    static **$serializer INSTANCE;
}
-keep @kotlinx.serialization.Serializable class * {
    *;
}
# Keep generated serializers
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ---- Retrofit / OkHttp ----
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ---- Firebase ----
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ---- Media3 / ExoPlayer ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- Coil ----
-keep class coil.** { *; }
-dontwarn coil.**

# ---- Songfy Data DTOs (dipakai kotlinx.serialization) ----
-keep class com.lagu.songfy.data.** { *; }

# ---- Palette ----
-keep class androidx.palette.** { *; }

# ---- Security Crypto / EncryptedSharedPreferences ----
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ---- DataStore ----
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ---- WorkManager ----
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ---- Suppress common third-party warnings ----
-dontwarn com.jakewharton.retrofit2.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**