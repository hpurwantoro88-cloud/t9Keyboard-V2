# Keep JNI methods and native bridge
-keepclassmembers class * {
    native <methods>;
}

-keep class com.opent9.keyboard.jni.** { *; }
-keep class com.opent9.keyboard.settings.** { *; }
