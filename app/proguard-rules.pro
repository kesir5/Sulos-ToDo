# Keep kotlinx.serialization models used for Drive sync
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.sueleyman.suelostodo.**$$serializer { *; }
-keepclassmembers class com.sueleyman.suelostodo.** { *** Companion; }
-keepclasseswithmembers class com.sueleyman.suelostodo.** { kotlinx.serialization.KSerializer serializer(...); }
