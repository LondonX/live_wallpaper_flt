# live_wallpaper_flt

**for now, it may crash on some vivo device, which running 68% Android 12 and 32% Android 13, known device codes are V2111, V2105, V2146, Y21A. [about this issue](https://github.com/flutter/flutter/issues/122900).**

# Usage
* Add service in `AndroidManifest.xml`
    ```xml
    <application>
        <service
          android:name="com.LondonX.live_wallpaper_flt.service.LiveWallpaperFltService"
          android:enabled="true"
          android:exported="true"
          android:permission="android.permission.BIND_WALLPAPER">
          <intent-filter>
              <action android:name="android.service.wallpaper.WallpaperService" />
          </intent-filter>
          <meta-data
              android:name="android.service.wallpaper"
              android:resource="@xml/live_wallpaper_flt" />
        </service>
    </application>
    ```

* Add `live_wallpaper_flt.xml` into `res/xml`
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <wallpaper xmlns:android="http://schemas.android.com/apk/res/android"
        android:author="@string/live_wallpaper_flt_author"
        android:description="@string/live_wallpaper_flt_description"
        android:thumbnail="@mipmap/live_wallpaper_flt_thumbnail" />
    ```

* Modify `main.dart`
    ```dart
    import 'package:live_wallpaper_flt/live_wallpaper_flt.dart';

    void main(List<String> args) {
    runApp(
        LiveWallpaperFlt.isStartFromService(args)
            ? Container(color: Colors.red)
            : const MyApp(),
    );
    }
    ```

* Set wallpaper
    ```dart
    if (Platform.isAndroid) {
    await LiveWallpaperFlt.instance.applyConfig();
    await LiveWallpaperFlt.instance.requestSetWallpaper();
    }
    ```
