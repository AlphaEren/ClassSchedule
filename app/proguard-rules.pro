# kotlinx.serialization：@Serializable 数据类与序列化器不能被裁掉
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lorat.classschedule.**$$serializer { *; }
-keepclassmembers class com.lorat.classschedule.** {
    *** Companion;
}
-keepclasseswithmembers class com.lorat.classschedule.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# navigation3 / Compose 反射用到的构造与导航 Key
-keep class com.lorat.classschedule.NavigationKt { *; }
-dontwarn org.jetbrains.annotations.**
