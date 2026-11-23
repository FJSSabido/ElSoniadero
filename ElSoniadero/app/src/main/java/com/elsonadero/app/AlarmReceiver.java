package com.elsonadero.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("id", 0);
        String label = "";
        if (id != AlarmScheduler.SNOOZE_ID) {
            AlarmStore.Alarm a = AlarmStore.get(context, id);
            if (a == null || !a.enabled) return;
            label = a.label;
            if (a.days == 0) {
                a.enabled = false;
                AlarmStore.save(context, a);
            } else {
                AlarmScheduler.schedule(context, a);
            }
        }
        Intent svc = new Intent(context, AlarmService.class)
                .setAction(AlarmService.ACTION_RING)
                .putExtra("label", label);
        context.startForegroundService(svc);
    }
}
