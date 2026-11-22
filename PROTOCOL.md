# Momax SW2S (IDO platform) Bluetooth protocol notes

Everything here was confirmed on a real SW2S (firmware 1.01) unless marked *unverified*.

## GATT

| UUID | Use |
|---|---|
| `0AF0` | service |
| `0AF6` | write (commands) |
| `0AF7` | notify (replies and events) |
| `0AF1` / `0AF2` | health write / notify (not needed so far) |
| `180F` / `2A19` | standard battery service |

The watch also has a classic Bluetooth side ("Momax SW2S BT", same address) for call audio (HFP) and AVRCP.

## Old-style ("v2") packets

20 bytes: `[cmd][key][18 bytes payload]`, written to `0AF6`; replies come on `0AF7` starting with the same `cmd key`.

| Packet | Meaning |
|---|---|
| `02 01` | device info. Reply: `02 01 <u16 id=0x1f42> <fw major> <fw minor> <mode> <battery %> …` |
| `02 A0` | live data. Reply: `u32 steps, u32 kcal, u32 distance m, u32 active` from byte 2. Byte 18 is not heart rate on this watch. |
| `03 01` | set time: `u16 year, month, day, hour, min, sec, weekday (Mon=0)` |
| `03 2D AA` | weather feature on (**required** for the weather screen to show data) |
| `03 44` | all-day SpO2: `AA/55 on/off, start h, start m, end h, end m, AA/55 low alert, threshold` |
| `03 53` | vibration motor: `on/off, u16 ms` *(unverified on SW2S)* |
| `05 01` | incoming call: chunks `total, index, 16 bytes` of body `[len(number), len(name)] number name` |
| `05 02 01` | call ended / answered: stop ringing |
| `05 03` | message notification (old format). **Shows the firmware placeholder "IDO test" on this watch.** Use v3 `0x0B`. |
| `06 01 00` | open the music screen |
| `06 04 00` / `06 04 01` | find device start / stop (acknowledged, but doesn't vibrate the SW2S) |
| `07 01 xx` (watch→phone) | music/camera buttons: `01` play (the SW2S sends `01` for every play/pause press), `02` pause, `04` previous, `05` next, `08`/`09` volume, `0C`/`0D` answer/reject call |
| `07 02` (watch→phone) | find my phone |
| `02 02` / `02 07` | function tables: which features the watch supports. Bit names are in the library's `protocol_func_table_*` structs (decoded list in `protocol_dump/sw2s_features.txt`, local only) |
| `02 30` / `03 29` | do not disturb: `AA/55 on, start h, m, end h, m, …` (verified by readback) |
| `02 B1` / `03 28` | raise to wake: `AA/55 on, show s, has range, start h, m, end h, m` (verified by readback) |
| `02 47` / `03 47` | walk reminder: `on 1/0, u16 steps, start h, m, end h, m, days<<1` (verified by readback) |
| `02 43` / `03 43` | calorie / distance goal: `u32 kcal, u32 metres, …` |
| `02 B0` / `03 32` | screen brightness: readable (level 60), but writes are acked and **ignored** on the SW2S |
| `03 20`, `03 60`, `03 55`, `03 03`, `03 10`, `03 11` | sedentary / drink water / hand washing reminders, step goal, profile, units: acked, effect *unverified* (no read command) |
| `07 40 …` (watch→phone) | "data changed" (e.g. `07 40 00 00 10` after a DND change, `04` after brightness) |

Notification icon types (v3 `0x0B` type field) follow the function-table bit order: 1 SMS, 2 email, 3 QQ, 4 WeChat, 5 Weibo, 6 Facebook, 7 Twitter, 8 WhatsApp, 9 Messenger, 10 Instagram, 11 LinkedIn, 12 calendar, 13 Skype, 16 VK, 17 Line, 18 Viber, 19 KakaoTalk, 20 Gmail, 21 Outlook, 22 Snapchat, 23 Telegram, 25 Slack, 28 YouTube, 32 TikTok. Type 8 (WhatsApp) verified on the SW2S 2026-11-21: shows the WhatsApp icon.

The complete command catalog extracted from the Momax library is in PROTOCOL_FULL.md.

## v3 frames

BLE packets that start with `0x33`. Reassembled, the frame is:

```
DA AD DA AD | 01 | u16 len (= payload + 11) | u16 cmd | u16 seq | payload | u16 CRC
```

* The CRC is Nordic `crc16_compute` (CCITT, init `0xFFFF`) over everything before it, little-endian.
* Frames longer than the MTU continue in further packets, each again starting with `0x33`.
* Health-sync frames (`cmd 0x04`) from the watch come **without** the CRC.
* Replies use the same `cmd` and `seq`.

| cmd | Meaning |
|---|---|
| `0x04` | health sync, see below |
| `0x06` | watch face list (no payload). Reply: short header, then entries `01, u32 len, name` (e.g. `w324.iwf`) |
| `0x07` | screen info (no payload). Reply includes `u16 width, u16 height` = 240 × 240 on the SW2S |
| `0x08` | set current face: name zero-padded to 31 bytes. Reply `00` + name = ok |
| `0x0A` | music info: `u8 status, u16 position s, u16 length s, u16 name len, name[64]` (the SW2S doesn't reply) |
| `0x0B` | **message notification**: `ver=1, flags=0, has_contact, has_phone, has_msg, u16 (app type | 0x3000), u32 id, u32 0, u16 0`, then for each present string `01, u32 len, bytes` (contact, phone, message). Reply `01`. |
| `0x0E` | set alarms, see below |
| `0x0F` | get alarms (payload `00`) |
| `0x35` | main UI sort / `0x78` menu list: no reply on the SW2S (firmware 1.01) |
| `0x3A` | weather, see below. Reply `03 00` = {version 3, error 0} |

### Alarms (`0x0E`)

Payload: `[0][10]` + 10 × 34-byte entries:

```
id, status, type, hour, minute, repeat, snooze, delay_min, shock_on_off, repeat_times, name[24]
```

* `status 0x55` adds or updates alarm `id`.
* `status 0xAA` **deletes** alarm `id`, and the following alarms shift down (ids renumber).
* All-zero entries are ignored.
* `repeat`: bit 0 = enabled, bits 1–7 = Mon…Sun. The official app uses `snooze 0, delay 5, shock 0, times 3`.
* To replace the list: send ten "delete id 1" entries, then the new alarms as ids 1…n.

### Health sync (`0x04`)

Request (`protocol_v3_health_head`): `[operate][type][is_today][u16 offset]`, operate `0` = send data, `1` = done.
Types: `1` SpO2, `2` stress, `3` heart rate, `4` activity, `7` sleep, `8` sport, `11` one-minute sport, `16` HRV.

Reply payload (`protocol_v3_health_head_reply`):

```
operate, type, is_today, u16 offset, u16 items remaining, u16 head length, u32 data length, u8 have_next_data, head, data
```

**Earlier days (verified 2026-11-22).** `is_today = 1` returns today since midnight. `is_today = 0` returns the
oldest *earlier* day the watch still holds (heart rate, stress and SpO2 for 2026-11-20 came back that way). Asking
again returns the same day until the app confirms it with operate `1` (same type, `is_today = 0`); the watch answers
with operate `1` echoed, and the next `is_today = 0` request returns the following day. When nothing is left, the
reply has 0 items and no data. Confirmed days are no longer offered, so the app must save them first (WatchLink
stores them in its own database before confirming). Activity (`4`) and HRV (`16`) returned nothing on the SW2S.

* `head` starts with `u16 year, month, day, u32 start time`.
* `data` items are `(u8 delta, u8 value)`. A value of 0 is filler (e.g. `ff 00` = 255-unit gap).
* Heart rate: start time and deltas are in **seconds** after midnight.
* Stress and SpO2: start time and deltas are in **minutes**.
* Sleep (verified on a real night, 2026-11-22): the head is `protocol_v3_sleep_head`, 40 bytes:

  | Offset | Field |
  |---|---|
  | 0 | version (3) |
  | 1 | data type (1) |
  | 2..7 | fell asleep: `u16 year`, month, day, hour, minute |
  | 8..13 | woke up, same layout (while still asleep: the time of the latest data) |
  | 14 | `u16` total minutes |
  | 16, 18, 20, 22 | `u16` awake, light, REM, deep minutes |
  | 24..27 | awake, light, REM, deep counts |
  | 28 | awrr status |
  | 29 | sleep score (83 on the test night) |
  | 30 | `u16` item count |
  | 32 | breath quality |
  | 33, 34, 35 | average heart rate, SpO2, breathing rate (`0xff` = not measured on the SW2S) |
  | 36..38 | nap splicing (`0xff`) |

  Items are `(u8 stage, u8 minutes)` in order from falling asleep. Stages: `1` awake, `2` light, `3` deep, `4` REM. The per-stage counts and minutes in the head match the items exactly. The watch rewrites the night about every 10 minutes while it goes on, sometimes regrouping the stages, so keep the latest reply. `is_today=0` returned the same (today's) night.
* `offset` skips that many items. Past the end, the watch starts over from 0.

### Weather (`0x3A`, version 3 layout)

```
3, month, day, hour, min, sec, weekday, type, temp, max, min,
u16 air quality, u16 rain %, humidity, UV, wind force,
flag(1 = 48 h + city), 48, days(7), city len, wind speed, 9 × 0,
sunrise h, sunrise m, sunset h, sunset m,
48 × (type, temp, rain %), 7 × (type, max, min), city name
```

* **Temperatures are °C + 100.**
* Weather type numbers used: 1 sunny, 2 cloudy, 3 overcast, 4 rain, 5 heavy rain, 6 storm, 7 snow, 8 sleet *(icons not all verified)*.
* The weather screen only shows data after `03 2D AA`.

## Firmware updates (how the official app does it; not implemented in WatchLink)

* The SW2S exposes only `1800`, `1801`, `180F` and `0AF0` over BLE. It has **no separate update service** while running normally.
* The official app (Flutter, IDO "Momax Wear" build) checks IDO's cloud for new firmware (`api/ota/dfu/checkUpdate`,
  plus separate checks for fonts, faces, GPS/EPO data and "extension firmware"), sending the device ID (`0x1F42` = 8002) and current version.
* The app supports three update stacks: Nordic DFU, IDO's own BLE DFU and SiFli OTA (platforms 98/99). The native
  library has `VBUS_EVT_APP_OTA_START`, `OTA_AUTH` and `OTA_DIRECT_START` events. In the old IDO protocol, the `01` command group is OTA. *Unverified:* `01 01`
  makes the watch reboot into its bootloader.
* In update mode the watch advertises one of these services: `00001530-1212-efde-1523-785feabcd123` (legacy Nordic DFU), `FE59`
  (Nordic secure DFU) or `00010203-0405-0607-0809-0a0b0c0d1912` (Telink OTA). The app then pushes the firmware image.
  The app's connection code checks for those services so it can resume an interrupted update.
* Faces, fonts and other files go over the normal `0AF0` link with the library's `data_tran` file transfer, not through the bootloader.

## Custom watch faces (verified 2026-11-21)

Worked out from `mkWatchFace::makeIwfFile`, `bitmap_tool_get_font_buff`, `fastlz_util_compress_file` and
`data_tran_*` in libprotocol_c.so. The image converter and FastLZ decoder were run under emulation
(`tools/emu_bitmap.py`, `tools/emu_fastlz.py`) and WatchLink's output matches them byte for byte.

**Watch limits** (v3 `0x07` screen info, v3 `0x31` face list): 240×240, image format `0x85`, block size 4096,
5 downloadable-face slots, 2 MB for faces.

**v3 `0x08`** = `u8 operate, name[30]`: operate `1` shows a face, `2` deletes it. Reply `err, operate, name`.

**`.iwf` file:** `"iwf\0"`, u16 version 1, u16 file count, then one 40-byte entry per file
(`name[30]`, 2 zero bytes, u32 offset from the start of the file, u32 length), then the file data.
Files: `iwf.json` (layout, same schema as the cloud faces: `custom` widgets `time`/`date`/`week`/`step`/
`battery`/`calorie` drawing digits from a font, plus `bkground`), `font.json`, the root images under their file
name (`files0.png`, `preview.png`), and font images named `<font>_<n>` (`g1_0` … `g1_10`, `week_en_mon` …).
Digit fonts have 11 images: 0–9 plus `:` (time), `/` (date) or `%` (battery); step/calorie fonts have 10.

**Images:** 16-byte head `"RAW\0"`, u16 w, u16 h, u8 format `0x85`, u8 alpha format (`0x66` = 4-bit alpha
plane, `0` = opaque), u16 0, u32 alpha offset; pixels RGB565 **big-endian**; then 4 bits of alpha per pixel
(even pixel in the high nibble), left out for fully opaque images.

**Compression:** the `.iwf` cut into 4096-byte blocks, each stored as u32 big-endian length + FastLZ level-2
data (a block that doesn't shrink is stored as two halves). Name it `<face>.iwf.lz`.

**Upload** (all on `0AF6`/`0AF7`, 20-byte headers):

| Packet | Content | Reply |
|---|---|---|
| `D1 08` | type 6 (iwf), u32 uncompressed size, compression **2** (FastLZ), name (≤12 bytes, else a longer packet) | `D1 08 err` |
| `D1 01` | type 6, u32 compressed size, 2, name | `D1 01 err` (`0x18`/`0x19` = busy making room, wait N s) |
| `D1 05` | packets per acknowledgement (10) | `D1 05 err, prn` |
| `D1 02` | `00` + up to MTU−3 bytes of data | every 10 packets: `D1 02 err, u32 byte sum so far, u32 offset` |
| `D1 03` | u32 sum of all bytes of the `.lz` file | `D1 03 00` = installed (the watch switches to it) |

Compression type 1 is accepted at every step but the face is silently dropped.

Fonts with `"bpp": 4` (and root images whose name contains `_4bit`) use image format `0x66`: the same 16-byte
head with format `0x66` and alpha format 0, then 4 bits of grey per pixel ((30R + 59G + 11B) / 100, top 4 bits).

**Images must have an even width.** The watch reads the 4-bit alpha plane as if each row starts on a new byte, so odd-width images (packed continuously, as the library does) come out skewed (seen on the SW2S 2026-11-21: digits drawn as diagonal dashes).

Analog hands: a `"widget": "watch", "type": "time"` item names hour/minute/second images (`hour`, `minute`,
`second`) with the rotation point inside each image (`hourcenterx/y` …) and on the screen (`houranchorx/y` …).
Widget types seen in the Momax app: `time`, `date`, `week`, `step`, `calorie`, `battery`; `heartrate` shows a number on the SW2S
(verified 2026-11-21). From the cloud-face samples: `weather` = temperature with a 13-image font (0–9, `-`,
`°C`, `°F`) and `"style": 2`; `heartrate` can use an 11th image (`-`, no reading); also `second`, `day` and
`apm` (2 images). `distance` (0–9 + `.`) shows on the SW2S (verified 2026-11-21). `second` (10 digits) and `apm` (images `apm_en_am`, `apm_en_pm`; shown only in 12-hour mode) also verified. The SW2S screen is round.
