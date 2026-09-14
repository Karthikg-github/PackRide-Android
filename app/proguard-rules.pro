# PackRide — keep Firebase models / reflection if needed
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}
