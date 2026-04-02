# Keep Xposed entry points
-keep class de.robv.android.xposed.** { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }

# Keep all Xposed module classes
-keep class io.github.hyperisland.xposed.** { *; }

# Keep isModuleActive so LSPosed can hook it by name in release builds
-keepclassmembers class io.github.hyperisland.MainActivity {
    public boolean isModuleActive();
}
