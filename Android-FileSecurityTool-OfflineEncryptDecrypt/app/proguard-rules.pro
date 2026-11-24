# Add project specific ProGuard rules here.
# Keep crypto classes (reflection usage by JCA/JCE)
-keepclassmembers class com.app.crypto.** { *; }
-keep class com.app.crypto.** { *; }

# Keep Room entities
-keep class com.app.data.entity.** { *; }
