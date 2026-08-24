# The app uses no reflection, no serialisation library and no dynamic class
# loading, so R8's defaults are almost enough on their own. These keeps are
# belt-and-braces for the pieces the framework instantiates by name from the
# manifest — if R8 ever renamed one, the failure would be an app that installs
# and then does nothing.
-keep class com.escposbridge.app.MainActivity { *; }
-keep class com.escposbridge.app.PrintBridgeService { *; }
-keep class com.escposbridge.app.BootReceiver { *; }

# Line numbers make a crash report from a shop tablet worth reading.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
