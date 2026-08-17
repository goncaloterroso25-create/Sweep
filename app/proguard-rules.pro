# Sweep keeps no reflection-based models, so the defaults are enough.
# Compose and Kotlin ship their own consumer rules.
-dontwarn org.jetbrains.annotations.**

# Strip development logging from release builds.
#
# The usage-history diagnostics added in v0.3 are already behind BuildConfig.DEBUG, so R8 removes
# them anyway. This is the belt to that pair of braces: it guarantees no verbose or debug logging
# about installed apps, file paths or usage coverage can reach a release build, whatever gets
# added later. Warnings and errors are kept, since those are worth having in a crash report.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
