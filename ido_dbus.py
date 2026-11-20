#!/usr/bin/env python3
"""
Talk to the Momax SW2S over BLE via BlueZ D-Bus directly (works even while the
watch is already LE-connected and not advertising).

  WATCH_MAC=AA:BB:CC:DD:EE:FF python ido_dbus.py 02 01   # send [02 01 00..] on 0AF6, print replies
  python ido_dbus.py 02 01 -- 02 04   # send several packets ('--' separates)
"""
import asyncio, sys
from dbus_fast.aio import MessageBus
from dbus_fast import BusType, Variant

import os
MAC = os.environ.get("WATCH_MAC", "")
if not MAC:
    sys.exit("set WATCH_MAC to your watch's Bluetooth address (see: python sw2s.py scan)")
DEV = "/org/bluez/hci0/dev_" + MAC.replace(":", "_")
SVC = DEV + "/service0014"
WRITE = SVC + "/char0015"            # 0AF6
NOTIFY = [SVC + "/char0017",         # 0AF7
          SVC + "/char001c"]         # 0AF2

async def iface(bus, path, name):
    intro = await bus.introspect("org.bluez", path)
    return bus.get_proxy_object("org.bluez", path, intro).get_interface(name)

async def main(packets, wait=5):
    bus = await MessageBus(bus_type=BusType.SYSTEM).connect()
    try:
        le = await iface(bus, DEV, "org.bluez.Bearer.LE1")
    except Exception:
        le = None
    if le is None or not await (await iface(bus, DEV, "org.bluez.Device1")).get_connected():
        # make sure BlueZ has seen the watch advertising over LE recently
        ad = await iface(bus, "/org/bluez/hci0", "org.bluez.Adapter1")
        await ad.call_set_discovery_filter({"Transport": Variant("s", "le")})
        await ad.call_start_discovery()
        print("scanning for watch...")
        for _ in range(60):
            await asyncio.sleep(0.5)
            try:
                le = await iface(bus, DEV, "org.bluez.Bearer.LE1")
                if await (await iface(bus, DEV, "org.bluez.Device1")).get_rssi(): break
            except Exception:
                pass
        await ad.call_stop_discovery()
        if le is None:
            sys.exit("watch not found - is it free (phone Bluetooth off / app closed)?")
    dev = await iface(bus, DEV, "org.bluez.Device1")
    if not await dev.get_connected():
        print("connecting over LE...")
        await le.call_connect()
        for _ in range(40):
            if await dev.get_services_resolved(): break
            await asyncio.sleep(0.25)
    for p in NOTIFY:
        props = await iface(bus, p, "org.freedesktop.DBus.Properties")
        tag = "0AF7" if p.endswith("0017") else "0AF2"
        def make(tag):
            def cb(i, changed, inv):
                if "Value" in changed:
                    print(f"<- [{tag}] {bytes(changed['Value'].value).hex(' ')}", flush=True)
            return cb
        props.on_properties_changed(make(tag))
        await (await iface(bus, p, "org.bluez.GattCharacteristic1")).call_start_notify()
    w = await iface(bus, WRITE, "org.bluez.GattCharacteristic1")
    for pkt in packets:
        pkt = pkt + bytes(20 - len(pkt))
        print(f"-> [0AF6] {pkt.hex(' ')}")
        await w.call_write_value(pkt, {"type": Variant("s", "request")})
        await asyncio.sleep(wait)

if __name__ == "__main__":
    groups, cur = [], []
    for a in sys.argv[1:] + ["--"]:
        if a == "--":
            if cur: groups.append(bytes(cur)); cur = []
        else:
            cur.append(int(a, 16))
    asyncio.run(main(groups or [bytes([0x02, 0x01])]))
