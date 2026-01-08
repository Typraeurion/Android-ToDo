# REQUIRED by MultiDex: This file defines which classes are
# in the primary DEX file, and thus loaded first.  It MUST
# include everything needed to load any secondary DEX files.

# Keep the MultiDex classes themselves
-keep class android.support.multidex.** { *; }
-keep class androidx.multidex.** { *; }

# Keep the Java 8+ desugaring library classes (j$)
## These are essential for instrumented tests to run on API < 21
-keep class j$.** { *; }
-keep interface j$.** { *; }

-keep class com.xmission.trevin.android.todo.ToDoApplication { *; }
# Keep our custom Test Runner
-keep class com.xmission.trevin.android.todo.util.MultiDexJUnitRunner { *; }

# Keep standard test runner and instrumentation classes
-keep class android.support.test.** { *; }
-keep class androidx.test.** { *; }
-keep class androidx.test.internal.** { *; }
