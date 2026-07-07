# Moshi's reflection-based adapter (no codegen -- see CLAUDE.md) matches JSON keys to Kotlin
# constructor parameter names via kotlin-reflect, reading them out of the kotlin.Metadata
# annotation. Both the annotation and the model classes' shape (fields/constructor) must survive
# shrinking and obfuscation intact, or JSON (de)serialization silently breaks at runtime.
-keep class kotlin.Metadata { *; }
-keep class com.kspay.forwarder.kpay.** { *; }
-keep class com.kspay.forwarder.net.** { *; }

# Room reads/writes this entity's fields by name via a generated DAO implementation.
-keep class com.kspay.forwarder.data.LocalTransaction { *; }

# androidx.security.crypto pulls in Google Tink, which references errorprone's compile-time-only
# annotations (RestrictedApi/Immutable/CheckReturnValue/CanIgnoreReturnValue). They aren't on the
# runtime classpath and are never actually invoked at runtime -- safe to ignore.
-dontwarn com.google.errorprone.annotations.**
