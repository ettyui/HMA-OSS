# Enum class
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class icu.nullptr.hidemyapplist.data.UpdateData { *; }
-keep class icu.nullptr.hidemyapplist.data.UpdateData$* { *; }

-keep,allowoptimization class * extends androidx.preference.PreferenceFragmentCompat
-keepclassmembers class org.frknkrc44.hma_oss.databinding.**  {
    public <methods>;
}
