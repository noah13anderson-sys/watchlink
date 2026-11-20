#!/usr/bin/env python3
"""
Minimal experimental client for the Momax SW2S (IDO-platform watch).

Findings from the Momax Fit app (com.momax.momaxwear.app):
  - BLE service      0x0AF0  (com.idosmart.bluetooth.ble.config.UUIDConfig.SERVICE_UUID)
  - write  (normal)  0x0AF6  WRITE_UUID_NORMAL
  - notify (normal)  0x0AF7  NOTIFY_UUID_NORMAL
  - write  (health)  0x0AF1  WRITE_UUID_HEALTH
  - notify (health)  0x0AF2  NOTIFY_UUID_HEALTH
  - notify (encrypt) 0x0AF8  NOTIFY_UUID_ENCRYPT_BGC  (only on some models)
  - Packets are max 20 bytes: [cmd][key][up to 18 payload bytes]
    (from protocol_write_set_cmd_key in libprotocol_c.so)

The specific cmd/key values below come from the public IDO protocol, not from
this app's code, so treat them as guesses to confirm on your watch.

Usage:
  python sw2s.py scan                 # find the watch
  python sw2s.py info  <MAC>          # list services + ask for device info
  python sw2s.py time  <MAC>          # set watch clock to PC time (experimental)
  python sw2s.py raw   <MAC> 02 01    # send any packet, print replies
"""
import asyncio, sys, datetime
from bleak import BleakScanner, BleakClient

def u(short):  # 16-bit UUID -> full 128-bit
    return f"0000{short:04x}-0000-1000-8000-00805f9b34fb"

SERVICE      = u(0x0AF0)
WRITE_NORMAL = u(0x0AF6)
NOTIFY_NORMAL= u(0x0AF7)
WRITE_HEALTH = u(0x0AF1)
NOTIFY_HEALTH= u(0x0AF2)
NOTIFY_ENC   = u(0x0AF8)

def packet(cmd, key, payload=b""):
    if len(payload) > 18:
        raise ValueError("payload max 18 bytes")
    return bytes([cmd, key]) + payload + bytes(18 - len(payload))

def on_notify(name):
    def h(_char, data: bytearray):
        print(f"<- [{name}] {data.hex(' ')}")
    return h

async def scan():
    print("Scanning 10s... (disconnect the watch from your phone first)")
    found = await BleakScanner.discover(timeout=10, return_adv=True)
    for dev, adv in found.values():
        ido = SERVICE in [s.lower() for s in adv.service_uuids]
        if ido or (dev.name and any(k in dev.name.upper() for k in ("SW", "MOMAX", "ID"))):
            print(f"{dev.address}  rssi={adv.rssi:4d}  name={dev.name!r}  {'<-- IDO service' if ido else ''}")

async def session(mac, packets, wait=4):
    async with BleakClient(mac, timeout=20) as c:
        print(f"Connected to {mac}")
        for s in c.services:
            print(f"service {s.uuid}")
            for ch in s.characteristics:
                print(f"   char {ch.uuid}  {','.join(ch.properties)}")
        for uuid, name in ((NOTIFY_NORMAL, "normal"), (NOTIFY_HEALTH, "health"), (NOTIFY_ENC, "encrypt")):
            if c.services.get_characteristic(uuid):
                await c.start_notify(uuid, on_notify(name))
                print(f"listening on {name} ({uuid[4:8]})")
        for p in packets:
            print(f"-> {p.hex(' ')}")
            await c.write_gatt_char(WRITE_NORMAL, p, response=True)
            await asyncio.sleep(wait)

def time_packet():
    n = datetime.datetime.now()
    # Guessed IDO set-time layout: cmd 0x03 key 0x01, year LE16, month, day, hour, min, sec, weekday(0=Mon)
    payload = n.year.to_bytes(2, "little") + bytes([n.month, n.day, n.hour, n.minute, n.second, n.weekday()])
    return packet(0x03, 0x01, payload)

def main():
    if len(sys.argv) < 2:
        print(__doc__); return
    cmd = sys.argv[1]
    if cmd == "scan":
        asyncio.run(scan())
    elif cmd == "info":
        asyncio.run(session(sys.argv[2], [packet(0x02, 0x01)]))   # guessed: get device info
    elif cmd == "time":
        asyncio.run(session(sys.argv[2], [time_packet()]))
    elif cmd == "raw":
        b = bytes(int(x, 16) for x in sys.argv[3:])
        asyncio.run(session(sys.argv[2], [packet(b[0], b[1], b[2:])]))
    else:
        print(__doc__)

if __name__ == "__main__":
    main()
