# kotlinx.serialization and OkHttp ship their own consumer rules.
# Keep the API models' names readable in crash reports.
-keepnames class app.unlimitedmessaging.android.api.** { *; }

# Crash events name the app's own classes and methods (Class#method, see
# analytics/Telemetry.kt): keep them unobfuscated so the names mean something.
# The source is public, so obfuscation hides nothing anyway.
-keepnames class app.unlimitedmessaging.android.** { *; }
