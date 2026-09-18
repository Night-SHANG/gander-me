# Local-reader R8 rules retained from Legado's upstream release configuration.
# Keep only rules needed by the reader/data/UI paths that VaultShelf exposes; online
# source/WebDAV/video stacks remain eligible for normal R8 dead-code removal.

# Gson/Room/Rhino rely on annotations, generic signatures and stable data members.
-keepattributes *Annotation*,InnerClasses,Signature
-keep class io.legado.app.data.entities.** { *; }

# Highlight styles are serialized by Gson outside data.entities.
-keep class io.legado.app.help.HighlightStyle { *; }
-keep class io.legado.app.help.HighlightStyle$** { *; }

# LiveEventBus reaches these internals reflectively.
-keepclassmembers class androidx.lifecycle.LiveData {
    *** mObservers;
    *** mActiveCount;
}
-keepclassmembers class androidx.arch.core.internal.SafeIterableMap {
    *** size();
    *** putIfAbsent(...);
}

# Legado's menu tint/overflow helpers use reflection on AppCompat internals.
-keepclassmembers class androidx.appcompat.widget.Toolbar {
    *** mNavButtonView;
}
-keepnames class androidx.appcompat.view.menu.SubMenuBuilder
-keep class androidx.appcompat.view.menu.MenuBuilder {
    *** setOptionalIconsVisible(...);
    *** getNonActionItems();
}

# FileDocExtensions reflectively opens TreeDocumentFile.
-keep class androidx.documentfile.provider.TreeDocumentFile {
    <init>(...);
}

# Preserve useful exception identity across the reader's error/reporting paths.
-keepnames class * extends java.lang.Throwable
-keepclassmembernames,allowobfuscation class * extends java.lang.Throwable { *; }
