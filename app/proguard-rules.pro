# PocketVM ProGuard Rules
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class com.pocketvm.data.model.** { *; }
-keep class com.pocketvm.engine.stub.** { *; }

# Keep reflection targets for hook engine
-keep class android.app.ActivityManager { *; }
-keep class android.content.pm.PackageManager { *; }
-keep class android.app.IActivityManager { *; }
-keep class android.content.pm.IPackageManager { *; }

# Suppress warnings for internal API reflections
-dontwarn android.app.ActivityThread
-dontwarn android.app.LoadedApk
-dontwarn dalvik.system.**
