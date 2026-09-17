# Ktor references an optional SLF4J binding that is not on Android.
-dontwarn org.slf4j.**
# Size: let R8 merge packages and widen access so it can inline and drop more.
-repackageclasses
-allowaccessmodification
