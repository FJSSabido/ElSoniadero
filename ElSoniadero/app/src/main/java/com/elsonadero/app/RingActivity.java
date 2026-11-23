package com.elsonadero.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/** Pantalla que aparece cuando suena la alarma, también con el móvil bloqueado. */
public class RingActivity extends Activity {

    public static final String ACTION_FINISH = "com.elsonadero.app.RING_FINISHED";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvClock;

    private final BroadcastReceiver finisher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Calendar c = Calendar.getInstance();
            tvClock.setText(Ui.time(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));
            handler.postDelayed(this, 10_000);
        }
    };

    /** Permite que la actividad se vea encima de la pantalla de bloqueo y encienda la pantalla. */
    static void showOverLock(Activity a) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            a.setShowWhenLocked(true);
            a.setTurnScreenOn(true);
        } else {
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLock(this);
        setContentView(R.layout.activity_ring);

        tvClock = findViewById(R.id.tvClock);
        TextView tvDate = findViewById(R.id.tvDate);
        tvDate.setText(new SimpleDateFormat("EEEE d 'de' MMMM", DreamStore.ES).format(new Date()));

        String label = getIntent().getStringExtra("label");
        if (label != null && !label.isEmpty()) {
            ((TextView) findViewById(R.id.tvGreeting)).setText(label);
        }

        findViewById(R.id.btnRecord).setOnClickListener(v -> {
            stopService(new Intent(this, AlarmService.class));
            startActivity(new Intent(this, RecordActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        findViewById(R.id.btnSnooze).setOnClickListener(v -> {
            startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_SNOOZE));
            Ui.toast(this, "Vuelvo a sonar en " + AlarmScheduler.SNOOZE_MINUTES + " minutos");
            finish();
        });
        findViewById(R.id.btnJustStop).setOnClickListener(v -> {
            stopService(new Intent(this, AlarmService.class));
            finish();
        });

        IntentFilter f = new IntentFilter(ACTION_FINISH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finisher, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(finisher, f);
        }
        if (!AlarmService.ringing) {
            // La alarma ya se apagó (por ejemplo desde la notificación).
            handler.post(this::finish);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        // Ignorado a propósito: la alarma se apaga con los botones.
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(finisher); } catch (Exception ignored) {}
        super.onDestroy();
    }

    static boolean isLocked(Context c) {
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && km.isKeyguardLocked();
    }
}
