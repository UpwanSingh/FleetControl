# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/upwansingh/Library/Android/sdk/tools/proguard/proguard-android.txt
# and each project's build.gradle file.

# Firebase
-keep class com.google.firebase.** { *; }

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Data Classes (Keep field names for Firestore SerDe)
-keepclassmembers class com.fleetcontrol.data.entities.** {
    <fields>;
}

# ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Coroutines
-dontwarn kotlinx.coroutines.**

# Drawables (Prevent R8 from stripping)
-keep class com.fleetcontrol.R$drawable { *; }
-keepclassmembers class com.fleetcontrol.R$drawable { *; }

# PinHasher (Keep security classes)

# SECURITY: Strip Logs in Release Builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
