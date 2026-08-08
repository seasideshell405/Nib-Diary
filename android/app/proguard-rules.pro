# R8 rules for the Diary app (release build).
# All project classes are plain Kotlin with no reflection, so default
# shrinking applies. Keep rules below cover library edge cases.

# kotlinx.serialization: serializers are referenced by generated code.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep all serializable types referenced by generated serializers
# (Kotlin 2.x / serialization 1.8 uses reified inline serializers).
-keep,includedescriptorclasses class com.diary.app.data.**$$serializer { *; }
-keepclassmembers class com.diary.app.data.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.diary.app.data.WireProfile { *; }
-keep,includedescriptorclasses class com.diary.app.data.WireEntry { *; }
-keep,includedescriptorclasses class com.diary.app.data.SyncRequest { *; }
-keep,includedescriptorclasses class com.diary.app.data.SyncResponse { *; }

# Room: generated implementations are located by name via reflection.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# okhttp
-dontwarn okhttp3.**
-dontwarn okio.**
