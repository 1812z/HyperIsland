# Keep Xposed entry points
-keep class de.robv.android.xposed.** { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }

# Keep all Xposed module classes
-keep class io.github.hyperisland.xposed.** { *; }

# libxposed reads this entry point from META-INF/xposed/java_init.list.
-keep class io.github.hyperisland.xposed.HyperIslandModule { *; }

# ScreenRecorderControlClient binds this service by the fixed class-name string in
# ScreenRecorderContract. Keep the complete IPC contract stable so the optimized
# manifest and the target recorder process always agree on the component name.
-keep class io.github.hyperisland.screenrecorder.ScreenRecorderControlService { *; }
-keep class io.github.hyperisland.screenrecorder.ScreenRecorderContract { *; }
-keep class io.github.hyperisland.screenrecorder.RecorderSnapshot { *; }

# Preserve metadata used by Kotlin/Compose and libxposed callback discovery.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
