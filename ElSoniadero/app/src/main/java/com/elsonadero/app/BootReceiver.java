package com.elsonadero.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Tras reiniciar el móvil o cambiar la hora, vuelve a programar las alarmas. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmScheduler.rescheduleAll(context);
    }
}
