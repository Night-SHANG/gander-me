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

# modules/rhino is source-merged into this library, so its original consumer rules
# must travel with the merged code instead of being lost with the module boundary.
-keep class
!org.htmlunit.corejs.javascript.ast.**,
!org.htmlunit.corejs.javascript.xml.**,
!org.htmlunit.corejs.javascript.commonjs.**,
!org.htmlunit.corejs.javascript.optimizer.**,
!org.htmlunit.corejs.javascript.serialize.**,
!org.htmlunit.corejs.javascript.tools.**,
org.htmlunit.corejs.javascript.** { *; }

-dontwarn org.htmlunit.corejs.javascript.engine.RhinoScriptEngineFactory
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Rhino exposes these implementations to user-authored local reader scripts by method name.
# The interface itself is @Keep upstream, but that does not preserve all implementors.
-keep class * implements io.legado.app.help.JsExtensions { *; }

# Optional/platform-only references already suppressed by Legado's reviewed release rules.
# Keeping these scoped warnings avoids turning an otherwise dead optional path into an R8 failure.
-dontwarn android.app.privatecompute.PccSandboxManager
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn com.gemalto.jp2.JP2Decoder



# Markwon HTML treats the CommonMark GFM strikethrough extension as optional.
# Its StrikeHandler probes the class with Class.forName and falls back to
# android.text.style.StrikethroughSpan when the extension is absent. This is
# the same scoped rule used by the pinned Legado release configuration.
-dontwarn org.commonmark.ext.gfm.**
