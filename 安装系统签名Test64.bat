set app_path=E:\workspace_lxl\A64Test\app\build\outputs\apk
java -jar sign64\signapk.jar sign64\platform.x509.pem sign64\platform.pk8 %app_path%\app-debug.apk %app_path%\Test64.apk
adb shell "am force-stop shine.com.test"
adb install -r %app_path%\Test64.apk
adb shell "am start -n shine.com.test/.activity.MainActivity"