package com.elsonadero.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Programa las alarmas en el sistema con setAlarmClock (suena incluso en reposo). */
public final class AlarmScheduler {

    public static final int SNOOZE_ID = 100000;
    public static final int SNOOZE_MINUTES = 9;

    private AlarmScheduler() {}

    private static PendingIntent firePi(Context c, int id) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("id", id);
        i.setAction("com.elsonadero.app.FIRE_" + id);
        return PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAt(Context c, int id, long when) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent show = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pi = firePi(c, id);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    public static void schedule(Context c, AlarmStore.Alarm a) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (!a.enabled) {
            am.cancel(firePi(c, a.id));
            return;
        }
        setAt(c, a.id, a.nextTrigger(System.currentTimeMillis()));
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(firePi(c, id));
    }

    public static void snooze(Context c) {
        setAt(c, SNOOZE_ID, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L);
    }

    public static void rescheduleAll(Context c) {
        List<AlarmStore.Alarm> all = AlarmStore.list(c);
        for (AlarmStore.Alarm a : all) schedule(c, a);
    }

    /** Próxima alarma activa, o -1. */
    public static long nextOverall(Context c) {
        long now = System.currentTimeMillis();
        long best = -1;
        for (AlarmStore.Alarm a : AlarmStore.list(c)) {
            if (!a.enabled) continue;
            long t = a.nextTrigger(now);
            if (best < 0 || t < best) best = t;
        }
        return best;
    }
}
