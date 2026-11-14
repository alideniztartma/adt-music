# ADT Music — R8 kuralları
# İsimleri karıştırma (hata ayıklaması kolay olsun); yine de kullanılmayan kod atılır ve optimize edilir.
-dontobfuscate
-ignorewarnings

# YouTube çıkarıcısı ve JavaScript motoru yansıma (reflection) kullanır
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.google.protobuf.** { *; }

-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn com.google.re2j.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# WorkManager işçileri yansıma ile oluşturulur
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
