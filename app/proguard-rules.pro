# ProGuard / R8 Rules for Universal File Explorer File Manager

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room & Coroutines
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Biometric
-keep class androidx.biometric.** { *; }

# PDFBox-Android & Apache Commons / Zip4j / XZ
-dontwarn org.apache.commons.compress.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.w3c.dom.**
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class net.lingala.zip4j.** { *; }
-keep class org.tukaani.xz.** { *; }

# ML Kit & ZXing
-dontwarn com.google.mlkit.**
-keep class com.google.zxing.** { *; }

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
