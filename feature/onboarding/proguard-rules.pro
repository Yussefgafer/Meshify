# Add project specific ProGuard rules here.
# For more details, see:
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
