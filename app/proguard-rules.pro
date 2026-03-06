# =========================================================
# SCROLLIOSIS MASTER: HARDENED & FUNCTIONAL (FINAL)
# =========================================================

# --- 1. CORE SYSTEM ENTRY POINTS ---
-keep class com.saltatoryimpulse.scrolliosis.GateService { *; }
-keep class com.saltatoryimpulse.scrolliosis.BootReceiver { *; }
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService { <init>(...); }
-keepclassmembers class * extends android.content.BroadcastReceiver { <init>(...); }

# --- 2. ROOM & DATA (SQL MAPPING PROTECTION) ---
-keep @androidx.room.Entity class *
-keep interface * extends androidx.room.RoomDatabase
-keep class com.saltatoryimpulse.scrolliosis.KnowledgeEntry { *; }
-keep class com.saltatoryimpulse.scrolliosis.BlockedApp { *; }
-keep interface com.saltatoryimpulse.scrolliosis.KnowledgeDao { *; }

# --- 3. LOG STRIPPING (SECURITY SEAL) ---
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** e(...);
}

# --- 4. COMPOSE, LIFECYCLE & OVERLAY STABILITY ---
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.saltatoryimpulse.scrolliosis.FakeLifecycleOwner { *; }

# Long-form explicit keeps for ViewTree Owners
-keepnames class androidx.lifecycle.setViewTreeLifecycleOwner { *; }
-keepnames class androidx.lifecycle.setViewTreeViewModelStoreOwner { *; }
-keepnames class androidx.savedstate.setViewTreeSavedStateRegistryOwner { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- 5. COROUTINE & SYSTEM SUPPRESSION ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.**
-dontwarn androidx.**