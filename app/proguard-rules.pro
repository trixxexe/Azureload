# Azureload ProGuard Rules

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Entities and Models
-keep class com.ritam.azureload.core.model.** { *; }
-keep class com.ritam.azureload.core.database.entity.** { *; }
