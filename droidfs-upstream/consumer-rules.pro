# Functional release rules retained from the pinned DroidFS application.
# The upstream app used -dontobfuscate globally; VaultShelf keeps only the classes,
# members and warnings that DroidFS actually accesses by JNI/reflection or framework lookup.

-dontwarn android.hardware.fingerprint.FingerprintManager
-dontwarn android.hardware.fingerprint.FingerprintManager$AuthenticationCallback
-dontwarn android.hardware.fingerprint.FingerprintManager$CryptoObject
-dontwarn androidx.camera.extensions.impl.**

-keep class sushi.hardcore.droidfs.SettingsActivity$**
-keep class sushi.hardcore.droidfs.explorers.ExplorerElement
-keepclassmembers class sushi.hardcore.droidfs.explorers.ExplorerElement {
    static sushi.hardcore.droidfs.explorers.ExplorerElement new(...);
}

# Keep all JNI entry points and their declaring classes addressable by native code.
-keepclasseswithmembernames class * {
    native <methods>;
}

# DroidFS intentionally reflects into ExifInterface so it can update rotation metadata
# without writing decrypted image bytes to ordinary storage.
-keepclassmembers class androidx.exifinterface.media.ExifInterface {
    int IMAGE_TYPE_JPEG;
    int IMAGE_TYPE_PNG;
    int IMAGE_TYPE_WEBP;
    int mMimeType;
    private void saveJpegAttributes(java.io.InputStream, java.io.OutputStream);
    private void savePngAttributes(java.io.InputStream, java.io.OutputStream);
    private void saveWebpAttributes(java.io.InputStream, java.io.OutputStream);
}

-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
