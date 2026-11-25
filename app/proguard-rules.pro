# 枚举常量名经 name()/valueOf() 持久化在 SharedPreferences 中（见 CryptoPrefs），
# 混淆常量名会导致已有配置读不回来，这里保留应用内枚举的成员名。
-keepclassmembers enum com.rootdb.studio.** {
    *;
}

# BouncyCastle 的 SM4 实现通过名称反射查找，保留整包
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
