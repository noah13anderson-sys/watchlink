# WatchLink

A small, open replacement for the **Momax Fit** app for the **Momax SW2S** smartwatch (an IDO-platform watch),
plus PC tools and notes on the watch's Bluetooth protocol.

> Not affiliated with Momax or IDO. Built for personal use and interoperability. No Momax or IDO code or
> binaries are included in this repository: the protocol notes were worked out by observing the watch and by
> studying how the official app talks to it.

## Features

| Feature | Status |
|---|---|
| Auto-find and connect to the watch (scans for the IDO `0AF0` service), auto-reconnect, starts on boot | ✅ |
| Set the watch clock on every connect | ✅ |
| Phone notifications → watch (sender and text, the app's own icon for WhatsApp, Gmail, Telegram…); choose which apps; skips silent ones and follows the phone's Do not disturb | ✅ |
| Incoming-call alerts (stop when answered/ended) | ✅ |
| Alarms (up to 10, days of week) | ✅ |
| Steps, calories, distance (live; activity calories as the watch shows them, plus the total with resting burn) | ✅ |
| Heart rate (latest, time, today's range) from the watch's health sync | ✅ |
| Stress | ✅ |
| Blood oxygen (turns on all-day SpO2 so the watch saves readings) | ✅ |
| Sleep: times, length, sleep score and stages (awake, REM, light, deep) with a chart of the night | ✅ |
| Weather (current, 48 h, 7 days, city) from Open-Meteo, MET Norway as fallback | ✅ |
| Find my phone (full volume, Stop button) | ✅ |
| Watch faces: list the faces installed on the watch and switch between them | ✅ |
| Face designer: digital (5 layouts) or analog, 14 fonts, 20 backgrounds or a photo, icons for steps, heart rate, calories, weather, distance and battery; installs straight to the watch (replacing your previous design when the watch is full) | ✅ |
| History: every heart rate, stress and blood oxygen reading, steps per 15 minutes, calories, nights with sleep stages and watch battery kept on the phone, with day-by-day charts and CSV export | ✅ |
| Earlier days: readings the watch took while the phone was away are fetched on the next connect | ✅ |
| Home screen widget (watch battery, steps, heart rate, blood oxygen) and a low watch battery alert | ✅ |
| Find my watch (a "Find watch" message every 5 s until Stop, at most 20 s) | ✅ |
| Music: watch play/pause/next/prev control the phone's player | ✅ play/pause; pair "Calls & music" for full AVRCP control |
| Battery & firmware | ✅ |

## Android app

Plain Java, no Gradle, no Android Studio. Needs JDK 17 and the Android SDK (build-tools 34, platform 34):

```bash
cd android
ANDROID_HOME=~/Android/sdk ./build.sh       # produces WatchLink.apk
adb install -r WatchLink.apk
```

On first launch allow the permissions, then grant **Notification access** (button in the app).
For call audio and full music control, tap **Calls & music** to pair the watch's classic Bluetooth side.
If you use WatchLink, disable Momax Fit so the two apps don't fight over the watch.

Developer hook (adb only, protected by the `DUMP` permission):

```bash
adb shell am broadcast -n dev.fishtank.watchlink/.DebugReceiver --es raw "0201"              # old-style packet
adb shell am broadcast -n dev.fishtank.watchlink/.DebugReceiver --ei v3cmd 15 --es v3 "00"   # v3 command
adb logcat -s WatchLink
```

## PC tools (Linux / BlueZ)

* `sw2s.py scan` finds the watch.
* `ido_dbus.py` sends raw packets over BlueZ D-Bus: `WATCH_MAC=AA:BB:CC:DD:EE:FF python ido_dbus.py 02 01`

Linux tip: connect over **LE only** (`org.bluez.Bearer.LE1.Connect`). A normal connect can pair the watch's
classic (calling) side with the PC, which takes the watch away from your phone.

## Protocol

See [PROTOCOL.md](PROTOCOL.md).
