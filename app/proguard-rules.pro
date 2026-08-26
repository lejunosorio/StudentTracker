# R8 rules for the release build.
#
# Most dependencies here ship their own consumer rules; what follows covers the pieces that are
# reached by JNI or reflection, where R8 cannot see the call and would otherwise strip or rename
# the target.

# Keep line numbers so a release stack trace can still be read, but drop the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- SQLCipher -------------------------------------------------------------------------------
# The native layer looks these up by name across the JNI boundary.
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# --- Tink, behind androidx.security.crypto ----------------------------------------------------
# Key types are resolved reflectively from the serialised key material.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# --- Room -------------------------------------------------------------------------------------
# Generated implementations are instantiated by name from the @Database class.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Root detection ---------------------------------------------------------------------------
-keep class com.scottyab.rootbeer.** { *; }
-dontwarn com.scottyab.rootbeer.**

# --- Barcode scanning -------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# --- Standard Android surfaces ----------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
