# TV Home

A light Home screen (launcher) for Android TV, made for the American Home / CultraView Android 9 TV (476 MB RAM).
Layout: a top bar (clock, Input, Network, Settings, Account, RAM), a big hero panel for the selected app, and one row of apps.

## Build

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
%USERPROFILE%\.gradle\wrapper\dists\gradle-9.3.1-all\<hash>\gradle-9.3.1\bin\gradle testDebugUnitTest assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the TV

```
adb -s 192.168.1.22:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.1.22:5555 shell cmd package set-home-activity dev.roel.tvhome/.MainActivity
```

The firmware ignores the default Home app, so the Home button is caught by an accessibility service.
This TV has no Accessibility screen, so turn it on with ADB (keep the TV's own HiRMService in the list):

```
adb -s 192.168.1.22:5555 shell settings put secure enabled_accessibility_services "com.hisilicon.rmservice/com.hisilicon.rmservice.HiRMService:dev.roel.tvhome/dev.roel.tvhome.HomeKeyService"
```

Never disable the stock launcher (`cn.cultraview.launcher.technical.open`): the TV hangs on its boot logo.

## Why Home works the way it does

On Home, this firmware opens its own launcher (on key release) and makes Android hold back any normal app's launch for about 5 s
(`Activity start request ... stopped` in logcat). No app can skip that without root. So TV Home covers the screen at once with a
picture of itself, keeps the cover up until TV Home is really back, then lifts it. The stock launcher is left loaded so it starts fast.

## Use

- OK opens an app. Hold OK to move it left or right, or hide it. The + tile shows hidden apps again.
- Hold OK on Settings to open tvfactory (ADB settings), or Activity Launcher if tvfactory has no icon.
- RAM shows memory available. Press it to stop apps waiting in the background (never Spotify).
- Auto-clean: 4 seconds after you come Home, the same clean runs by itself. Hold OK on RAM to turn it on or off (on by default).

## Credits

Font: Poppins, SIL Open Font License (see `OFL-Poppins.txt`). Icons: Material icons shapes (Apache 2.0).
