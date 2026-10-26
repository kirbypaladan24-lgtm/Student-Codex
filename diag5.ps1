$adb = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "getprop ro.build.version.sdk; getprop ro.bootmode; getprop dev.bootcomplete; getprop sys.boot_completed; getprop service.bootanim.exit"
& $adb shell "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.example.studentcodex"