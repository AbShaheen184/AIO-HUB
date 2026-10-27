# ---------------------------------------------------------------------------
# AIO Hub release ProGuard/R8 rules.
#
# The app ships prebuilt native libraries (libretro cores, PPSSPP, ffmpeg), so a
# few packages have to survive shrinking.
# ---------------------------------------------------------------------------

# --- Keep the JNI bridge in the com.app.mlounge.emulator package ----------
# The prebuilt libretrowrapper.so exports symbols named
# Java_com_app_mlounge_emulator_LibretroCore_*. R8 must not rename or remove
# them or every native call fails at runtime.
#
# The class keeps the original's package name for that reason alone: a JNI
# symbol is built from the package and the class name, so the shim cannot move
# without recompiling the .so. Everything else of the emulator lives in
# com.movies.premium.emulator.
-keepclasseswithmembernames class com.app.mlounge.emulator.** {
    native <methods>;
}
-keep class com.app.mlounge.emulator.LibretroCore { *; }
-keep class com.movies.premium.emulator.** { *; }

# --- Retrofit / OkHttp ------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Retrofit service interfaces and their response models.
-keep interface com.movies.premium.data.remote.** { *; }
-keep class com.movies.premium.data.remote.model.** { *; }
-keep class com.movies.premium.data.remote.api.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp platform hooks.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Gson -------------------------------------------------------------------
# The wire models are reflected over, so their field names and annotations
# must survive. The models themselves are already kept above; this protects
# the annotation and any type-token usage.
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.annotations.SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# --- Room -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Kotlin coroutines ------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Coil 3 -----------------------------------------------------------------
-dontwarn coil3.**

# --- Media3 / ExoPlayer -----------------------------------------------------
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }

# --- Hilt / Dagger ----------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembernames class * {
    @dagger.hilt.android.AndroidEntryPoint <init>(...);
}

# --- The app's own provider implementations (the Pluto TV provider and friends).
-keep class com.movies.premium.data.providers.** { *; }

# --- Room entities and DAOs ------------------------------------------------
# Room embeds the column names in the SQL it generates, so the entities do not
# have to survive shrinking for the schema to stay correct. This rule is
# insurance for the other direction: it keeps the entity and DAO names
# readable in a release stack trace.
-keep class com.movies.premium.data.local.entity.** { *; }
-keep class com.movies.premium.data.local.dao.** { *; }

# --- Desugaring -------------------------------------------------------------
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn sun.misc.**

# --- jsoup / rhino (anime scrapers) ----------------------------------------
-dontwarn org.jsoup.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }
