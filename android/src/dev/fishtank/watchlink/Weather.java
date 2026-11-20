package dev.fishtank.watchlink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Weather for the watch (v3 cmd 0x3A, "version 3" layout from protocol_set_v3_weather_send_version0x3).
 * Forecast from Open-Meteo (free, no key). Location is the phone's last known coarse location,
 * saved whenever the app is open so the background service can use it.
 */
final class Weather {
    private Weather() {}
    static final int V3_WEATHER = 0x3A;
    static volatile String summary = "";

    /** Remember the phone's location + city (call while the app is in the foreground). */
    @SuppressLint("MissingPermission")
    static void saveLocation(Context c) {
        try {
            LocationManager lm = c.getSystemService(LocationManager.class);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null) return;
            final Location loc = best;
            new Thread(() -> {
                String city = "";
                try {
                    List<Address> a = new Geocoder(c, Locale.getDefault()).getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                    if (a != null && !a.isEmpty()) {
                        city = a.get(0).getLocality();
                        if (city == null) city = a.get(0).getSubAdminArea();
                        if (city == null) city = "";
                    }
                } catch (Exception ignored) {}
                c.getSharedPreferences(WatchService.PREFS, 0).edit()
                        .putFloat("lat", (float) loc.getLatitude()).putFloat("lon", (float) loc.getLongitude())
                        .putString("city", city).apply();
            }).start();
        } catch (SecurityException e) {
            Log.w(WatchService.TAG, "no location permission");
        }
    }

    /** Fetch the forecast and send it to the watch (runs on a background thread). */
    static void update(Context c) {
        SharedPreferences p = c.getSharedPreferences(WatchService.PREFS, 0);
        if (!p.contains("lat")) { summary = "Open WatchLink once to set your location"; return; }
        float lat = p.getFloat("lat", 0), lon = p.getFloat("lon", 0);
        String city = p.getString("city", "");
        new Thread(() -> {
            try {
                String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max"
                        + "&timezone=auto&forecast_days=7", lat, lon);
                byte[] payload;
                String src;
                try {
                    JSONObject j = new JSONObject(get(url));
                    payload = build(j, city);
                    JSONObject cur = j.getJSONObject("current");
                    summary = Math.round(cur.getDouble("temperature_2m")) + "° " + describe(cur.getInt("weather_code"));
                    src = "Open-Meteo";
                } catch (Exception first) {
                    Log.w(WatchService.TAG, "Open-Meteo failed (" + first + "), trying MET Norway");
                    JSONObject j = new JSONObject(get(String.format(Locale.US,
                            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.3f&lon=%.3f", lat, lon)));
                    payload = buildMetNo(j, city);
                    src = "MET Norway";
                }
                WatchService.sendV3(V3_WEATHER, payload);
                summary = summary + (city.isEmpty() ? "" : " · " + city);
                Log.i(WatchService.TAG, "weather from " + src);
                Log.i(WatchService.TAG, "weather sent: " + summary);
                MainActivity.refresh();
            } catch (Exception e) {
                Log.w(WatchService.TAG, "weather failed: " + e);
            }
        }).start();
    }

    private static String get(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        con.setRequestProperty("User-Agent", "WatchLink/0.1 (personal smartwatch app)");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally { con.disconnect(); }
    }

    /** WMO weather code -> IDO weather type (IDO numbering is the usual one; icons not yet verified). */
    static int idoType(int wmo) {
        if (wmo == 0) return 1;                                   // sunny
        if (wmo == 1 || wmo == 2) return 2;                       // cloudy / partly cloudy
        if (wmo == 3 || wmo == 45 || wmo == 48) return 3;         // overcast (fog shown as overcast)
        if (wmo >= 95) return 6;                                  // thunderstorm
        if (wmo == 66 || wmo == 67) return 8;                     // sleet / freezing rain
        if ((wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86) return 7;  // snow
        if (wmo == 65 || wmo == 82) return 5;                     // heavy rain
        if (wmo >= 51) return 4;                                  // drizzle / rain / showers
        return 0;
    }

    static String describe(int wmo) {
        switch (idoType(wmo)) {
            case 1: return "Sunny"; case 2: return "Cloudy"; case 3: return "Overcast"; case 4: return "Rain";
            case 5: return "Heavy rain"; case 6: return "Storms"; case 7: return "Snow"; case 8: return "Sleet";
            default: return "";
        }
    }

    /** The watch stores temperatures as °C + 100 (it showed -79 for 21). */
    private static int temp(double t) { return ((int) Math.round(t) + 100) & 0xff; }

    static byte[] build(JSONObject j, String city) throws Exception {
        Calendar n = Calendar.getInstance();
        JSONObject cur = j.getJSONObject("current");
        JSONObject d = j.getJSONObject("daily");
        JSONObject h = j.getJSONObject("hourly");
        byte[] cityB = IdoProtocol.utf8(city, 75);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(3);                                               // version 3
        b.write(n.get(Calendar.MONTH) + 1); b.write(n.get(Calendar.DAY_OF_MONTH));
        b.write(n.get(Calendar.HOUR_OF_DAY)); b.write(n.get(Calendar.MINUTE)); b.write(n.get(Calendar.SECOND));
        b.write((n.get(Calendar.DAY_OF_WEEK) + 5) % 7);           // Monday = 0
        b.write(idoType(cur.getInt("weather_code")));
        b.write(temp(cur.getDouble("temperature_2m")));
        b.write(temp(d.getJSONArray("temperature_2m_max").getDouble(0)));
        b.write(temp(d.getJSONArray("temperature_2m_min").getDouble(0)));
        b.write(0); b.write(0);                                   // air quality (unknown)
        int rain = d.getJSONArray("precipitation_probability_max").optInt(0, 0);
        b.write(rain); b.write(0);
        b.write(cur.getInt("relative_humidity_2m"));
        b.write((int) Math.round(d.getJSONArray("uv_index_max").optDouble(0, 0)));
        double wind = cur.getDouble("wind_speed_10m");            // km/h
        b.write(beaufort(wind));                                  // wind force
        b.write(1);                                               // has 48 hours + city name
        b.write(48);                                              // hours
        b.write(7);                                               // days
        b.write(cityB.length);
        b.write((int) Math.round(wind) & 0xff);                   // wind speed
        for (int i = 0; i < 9; i++) b.write(0);
        int[] sr = hm(d.getJSONArray("sunrise").getString(0)), ss = hm(d.getJSONArray("sunset").getString(0));
        b.write(sr[0]); b.write(sr[1]); b.write(ss[0]); b.write(ss[1]);
        // 48 hours starting from the current hour
        JSONArray ht = h.getJSONArray("time"), htemp = h.getJSONArray("temperature_2m"),
                hcode = h.getJSONArray("weather_code"), hprob = h.getJSONArray("precipitation_probability");
        String nowHour = String.format(Locale.US, "%04d-%02d-%02dT%02d:00", n.get(Calendar.YEAR),
                n.get(Calendar.MONTH) + 1, n.get(Calendar.DAY_OF_MONTH), n.get(Calendar.HOUR_OF_DAY));
        int start = 0;
        for (int i = 0; i < ht.length(); i++) if (ht.getString(i).equals(nowHour)) { start = i; break; }
        for (int i = 0; i < 48; i++) {
            int k = Math.min(start + i, ht.length() - 1);
            b.write(idoType(hcode.getInt(k)));
            b.write(temp(htemp.getDouble(k)));
            b.write(hprob.optInt(k, 0));
        }
        // 7 days
        JSONArray dc = d.getJSONArray("weather_code"), dmax = d.getJSONArray("temperature_2m_max"),
                dmin = d.getJSONArray("temperature_2m_min");
        for (int i = 0; i < 7; i++) {
            int k = Math.min(i, dc.length() - 1);
            b.write(idoType(dc.getInt(k)));
            b.write(temp(dmax.getDouble(k)));
            b.write(temp(dmin.getDouble(k)));
        }
        b.write(cityB, 0, cityB.length);
        return b.toByteArray();
    }

    /** MET Norway symbol code -> IDO weather type. */
    static int idoTypeFromSymbol(String sym) {
        if (sym == null) return 0;
        if (sym.contains("thunder")) return 6;
        if (sym.contains("sleet")) return 8;
        if (sym.contains("snow")) return 7;
        if (sym.startsWith("heavyrain")) return 5;
        if (sym.contains("rain")) return 4;
        if (sym.startsWith("clearsky")) return 1;
        if (sym.startsWith("fair") || sym.startsWith("partlycloudy")) return 2;
        if (sym.startsWith("cloudy") || sym.startsWith("fog")) return 3;
        return 0;
    }

    private static String symbol(JSONObject data) {
        for (String k : new String[]{"next_1_hours", "next_6_hours", "next_12_hours"}) {
            JSONObject o = data.optJSONObject(k);
            if (o != null && o.optJSONObject("summary") != null) return o.optJSONObject("summary").optString("symbol_code");
        }
        return null;
    }

    /** Same watch payload, built from MET Norway's compact forecast (no sunrise/UV there; sent as 0). */
    static byte[] buildMetNo(JSONObject j, String city) throws Exception {
        Calendar n = Calendar.getInstance();
        JSONArray ts = j.getJSONObject("properties").getJSONArray("timeseries");
        java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        java.text.SimpleDateFormat dayKey = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US);
        // hourly entries from now on, and per-day max/min/most common type
        java.util.List<int[]> hours = new java.util.ArrayList<>();
        java.util.LinkedHashMap<String, int[]> days = new java.util.LinkedHashMap<>();   // {max, min, type}
        long now = System.currentTimeMillis() - 3600_000L;
        for (int i = 0; i < ts.length(); i++) {
            JSONObject e = ts.getJSONObject(i);
            long t = iso.parse(e.getString("time")).getTime();
            JSONObject data = e.getJSONObject("data");
            double temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            int type = idoTypeFromSymbol(symbol(data));
            if (t >= now && hours.size() < 48) hours.add(new int[]{type, (int) Math.round(temp), 0});
            String k = dayKey.format(new java.util.Date(t));
            int[] d = days.get(k);
            if (d == null) days.put(k, new int[]{(int) Math.round(temp), (int) Math.round(temp), type});
            else { d[0] = Math.max(d[0], (int) Math.round(temp)); d[1] = Math.min(d[1], (int) Math.round(temp));
                   if (type > d[2]) d[2] = type; }
        }
        JSONObject first = ts.getJSONObject(0).getJSONObject("data");
        JSONObject inst = first.getJSONObject("instant").getJSONObject("details");
        double windKmh = inst.optDouble("wind_speed", 0) * 3.6;
        int curType = idoTypeFromSymbol(symbol(first));
        int curTemp = (int) Math.round(inst.getDouble("air_temperature"));
        int[] today = days.get(dayKey.format(n.getTime()));
        if (today == null) today = new int[]{curTemp, curTemp, curType};
        summary = curTemp + "° " + describeType(curType);

        byte[] cityB = IdoProtocol.utf8(city, 75);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(3);
        b.write(n.get(Calendar.MONTH) + 1); b.write(n.get(Calendar.DAY_OF_MONTH));
        b.write(n.get(Calendar.HOUR_OF_DAY)); b.write(n.get(Calendar.MINUTE)); b.write(n.get(Calendar.SECOND));
        b.write((n.get(Calendar.DAY_OF_WEEK) + 5) % 7);
        b.write(curType); b.write(curTemp + 100); b.write(today[0] + 100); b.write(today[1] + 100);
        b.write(0); b.write(0);                                   // air quality
        b.write(0); b.write(0);                                   // rain probability
        b.write((int) Math.round(inst.optDouble("relative_humidity", 0)));
        b.write(0);                                               // UV
        b.write(beaufort(windKmh));
        b.write(1); b.write(48); b.write(7); b.write(cityB.length);
        b.write((int) Math.round(windKmh) & 0xff);
        for (int i = 0; i < 9; i++) b.write(0);
        b.write(0); b.write(0); b.write(0); b.write(0);           // sunrise / sunset unknown
        for (int i = 0; i < 48; i++) {
            int[] hr = hours.isEmpty() ? new int[]{curType, curTemp, 0} : hours.get(Math.min(i, hours.size() - 1));
            b.write(hr[0]); b.write(hr[1] + 100); b.write(hr[2]);
        }
        java.util.List<int[]> dl = new java.util.ArrayList<>(days.values());
        for (int i = 0; i < 7; i++) {
            int[] d = dl.isEmpty() ? today : dl.get(Math.min(i, dl.size() - 1));
            b.write(d[2]); b.write(d[0] + 100); b.write(d[1] + 100);
        }
        b.write(cityB, 0, cityB.length);
        return b.toByteArray();
    }

    static String describeType(int t) {
        switch (t) {
            case 1: return "Sunny"; case 2: return "Cloudy"; case 3: return "Overcast"; case 4: return "Rain";
            case 5: return "Heavy rain"; case 6: return "Storms"; case 7: return "Snow"; case 8: return "Sleet";
            default: return "";
        }
    }

    private static int[] hm(String iso) {   // "2026-11-20T06:12"
        try {
            String t = iso.substring(iso.indexOf('T') + 1);
            return new int[]{Integer.parseInt(t.substring(0, 2)), Integer.parseInt(t.substring(3, 5))};
        } catch (Exception e) { return new int[]{0, 0}; }
    }

    private static int beaufort(double kmh) {
        double[] lim = {1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118};
        for (int i = 0; i < lim.length; i++) if (kmh < lim[i]) return i;
        return 12;
    }
}
