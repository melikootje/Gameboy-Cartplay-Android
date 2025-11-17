# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Hilt components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembernames class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}

# Keep Timber
-keep class timber.log.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep data classes used by mock interface
-keep class com.gbaoperator.plugin.data.** { *; }
-keep class com.gbaoperator.plugin.emulator.** { *; }

# Keep USB related classes
-keep class android.hardware.usb.** { *; }

# Don't warn about missing classes that might not be available on all devices
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep generic signatures for serialization
-keepattributes Signature

# Keep annotations
-keepattributes *Annotation*
