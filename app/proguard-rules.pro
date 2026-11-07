# kotlinx.serialization and OkHttp ship their own consumer rules.
# Keep the API models' names readable in crash reports.
-keepnames class app.unlimitedmessaging.android.api.** { *; }
