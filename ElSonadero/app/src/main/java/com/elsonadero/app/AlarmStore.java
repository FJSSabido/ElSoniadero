package com.elsonadero.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Alarmas guardadas en SharedPreferences como JSON. */
public final class AlarmStore {

    public static final class Alarm {
        public int id;
        public int hour = 7;
        public int minute = 30;
        /** bit0 = lunes … bit6 = domingo. 0 = una sola vez. */
        public int days = 0b0011111;
        public boolean enabled = true;
        public String label = "";

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("hour", hour);
            o.put("minute", minute);
            o.put("days", days);
            o.put("enabled", enabled);
            o.put("label", label);
            return o;
        }

        static Alarm fromJson(JSONObject o) {
            Alarm a = new Alarm();
            a.id = o.optInt("id");
            a.hour = o.optInt("hour", 7);
            a.minute = o.optInt("minute", 30);
            a.days = o.optInt("days", 0);
            a.enabled = o.optBoolean("enabled", true);
            a.label = o.optString("label", "");
            return a;
        }

        /** Próximo instante (ms) en que debe sonar, a partir de "now". */
        public long nextTrigger(long now) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < 8; i++) {
                if (c.getTimeInMillis() > now && (days == 0 || (days & (1 << dayIndex(c))) != 0)) {
                    return c.getTimeInMillis();
                }
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
            return c.getTimeInMillis();
        }
    }

    /** Lunes = 0 … domingo = 6. */
    public static int dayIndex(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    private static final String PREFS = "alarms";

    private AlarmStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Alarm> list(Context c) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(Alarm.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        out.sort((a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));
        return out;
    }

    public static synchronized Alarm get(Context c, int id) {
        for (Alarm a : list(c)) if (a.id == id) return a;
        return null;
    }

    private static void write(Context c, List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : alarms) arr.put(a.toJson());
        } catch (Exception ignored) {}
        prefs(c).edit().putString("list", arr.toString()).apply();
    }

    public static synchronized void save(Context c, Alarm alarm) {
        List<Alarm> all = list(c);
        if (alarm.id == 0) {
            int next = prefs(c).getInt("nextId", 1);
            alarm.id = next;
            prefs(c).edit().putInt("nextId", next + 1).apply();
        }
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == alarm.id) {
                all.set(i, alarm);
                replaced = true;
            }
        }
        if (!replaced) all.add(alarm);
        write(c, all);
    }

    public static synchronized void delete(Context c, int id) {
        List<Alarm> all = list(c);
        all.removeIf(a -> a.id == id);
        write(c, all);
    }
}
