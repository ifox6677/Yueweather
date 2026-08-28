# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/yonjuni/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# WorkManager instantiates workers reflectively by class name stored in its
# database, so they must not be renamed or stripped in release builds.
# (work-runtime ships consumer rules for androidx.work internals, but the
# app's own ListenableWorker subclasses are not guaranteed to be kept.)
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

