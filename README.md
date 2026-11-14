# Zero Home

A light Home screen (launcher) for low-RAM Android TVs, built for TVs whose firmware forces its own launcher.

- **Top bar:** clock and date, plus Input (TV/HDMI), Network, Settings, Account and a RAM button.
- **Hero panel:** a big view of the selected app (its TV banner and name).
- **One app row:** move or hide apps; the **+** tile brings hidden apps back.
- **Auto-clean:** when you come back Home, apps waiting in the background are stopped (Spotify is kept), so the next app has more memory.
- **Home key override:** works even when the firmware ignores the chosen default launcher.
- No WebView, no libraries, plain Java. About 25 MB of RAM. Font: Poppins.

Tested on an American Home (HiDPT / CultraView, HiSilicon) Android 9 TV with 476 MB RAM.
The Input, Network, Settings and Account buttons open that firmware's screens. On other TVs they may show "Not available on this TV".

## Install

You need ADB on a PC and network debugging turned on on the TV. Replace `TV_IP` with your TV's address.

1. Download `ZeroHome-v1.0.0.apk` from [Releases](../../releases) and install it:
   ```
   adb connect TV_IP:5555
   adb -s TV_IP:5555 install -r ZeroHome-v1.0.0.apk
   adb -s TV_IP:5555 shell cmd package set-home-activity dev.roel.tvhome/.MainActivity
   ```
2. Turn on the Home key service. Many TVs have no Accessibility screen, so use ADB. First read what is already on, and **keep it**:
   ```
   adb -s TV_IP:5555 shell settings get secure enabled_accessibility_services
   ```
   Then add Zero Home after it, with a `:` in between. Example, with one existing service:
   ```
   adb -s TV_IP:5555 shell settings put secure enabled_accessibility_services "com.hisilicon.rmservice/com.hisilicon.rmservice.HiRMService:dev.roel.tvhome/dev.roel.tvhome.HomeKeyService"
   ```
   If another launcher's Home override is in that list, leave it out: two overrides fight each other.
3. Press Home on the remote.

**Undo:** set the services list back to what it was, then `adb uninstall dev.roel.tvhome`.

**Warning:** don't disable your TV's stock launcher to "help" Zero Home. On the tested TV this made it hang on the boot logo until a factory reset.

## Use

- **OK** opens an app. **Hold OK** to move it left or right, or to hide it.
- **Hold OK on Settings** to open `com.tvfactory` (hidden ADB settings on CultraView TVs), or Activity Launcher if that has no icon.
- **RAM** shows the memory available. Press it to clean now. **Hold OK on RAM** to turn auto-clean on or off (on by default).

## How the Home key works

On Home, this kind of firmware opens its own launcher on key release, then makes Android hold back every normal app's launch for about 5 s
(logcat: `Activity start request ... stopped`). Apps can't skip that without root. So the moment Home is pressed, Zero Home covers the
screen with a picture of itself (an accessibility overlay), keeps it up until Zero Home is really back, then lifts it.
You see your home screen at once; the remote may not respond for up to about 5 s on some presses.

## Build

Needs Android Studio's JDK and Gradle 9.3+ (Android Gradle Plugin 9.1).

```
gradle testDebugUnitTest assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`. Release APKs are signed with a debug key, so install updates from this repo's releases only.

## Credits

Font: [Poppins](https://github.com/itfoundry/Poppins), SIL Open Font License (see `OFL-Poppins.txt`). Icons: Material icons shapes (Apache 2.0).
