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
| `07 40 …` (watch→phone) | sent on connect; meaning unknown |

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
| `0x0A` | music info: `u8 status, u16 position s, u16 length s, u16 name len, name[64]` (the SW2S doesn't reply) |
| `0x0B` | **message notification**: `ver=1, flags=0, has_contact, has_phone, has_msg, u16 (app type | 0x3000), u32 id, u32 0, u16 0`, then for each present string `01, u32 len, bytes` (contact, phone, message). Reply `01`. |
| `0x0E` | set alarms, see below |
| `0x0F` | get alarms (payload `00`) |
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

Request: `[0 = start][type][1][u16 offset]`.
Types: `1` SpO2, `2` stress, `3` heart rate, `4` activity, `7` sleep, `8` sport, `11` one-minute sport, `16` HRV.

Reply payload:

```
echo(5), u16 items remaining, u16 head length, u16 data length, …, head, data
```

* `head` starts with `u16 year, month, day, u32 start time`.
* `data` items are `(u8 delta, u8 value)`. A value of 0 is filler (e.g. `ff 00` = 255-unit gap).
* Heart rate: start time and deltas are in **seconds** after midnight.
* Stress and SpO2: start time and deltas are in **minutes** (SpO2 *unverified*).
* Sleep: 40-byte head with `u16 total minutes` at head+6 and the stage count at head+22. Items are `(stage, minutes)`; stage codes are *unverified*.
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
