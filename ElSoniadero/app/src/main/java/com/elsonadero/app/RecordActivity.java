package com.elsonadero.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

/** Graba el sueño nada más apagar la alarma (o al pulsar "Grabar ahora"). */
public class RecordActivity extends Activity {

    private static final int REQ_MIC = 21;
    private static final String[] PROMPTS = {
            "¿Qué has soñado?", "¿Dónde estabas?", "¿Quién aparecía?",
            "¿Qué sentías?", "¿Algo extraño o imposible?", "¿Cómo terminaba?"
    };

    private MediaRecorder recorder;
    private File outFile;
    private long startedAt;
    private long startElapsed;
    private long pausedTotal;
    private long pausedAt;
    private boolean recording;
    private boolean paused;
    private boolean finished;
    private int promptIndex;
    private float level;

    private TextView tvTimer, tvPrompt, tvStatus;
    private Button btnPause;
    private android.view.View dot, halo;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable meter = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            tvTimer.setText(Ui.duration(elapsed()));
            float target = 0f;
            if (!paused) {
                try { target = Math.min(1f, recorder.getMaxAmplitude() / 12000f); } catch (Exception ignored) {}
            }
            level = level * 0.6f + target * 0.4f;
            float s = 1f + level * 1.1f;
            dot.setScaleX(s);
            dot.setScaleY(s);
            float hs = 1f + level * 0.5f;
            halo.setScaleX(hs);
            halo.setScaleY(hs);
            handler.postDelayed(this, 80);
        }
    };

    private final Runnable rotatePrompt = new Runnable() {
        @Override
        public void run() {
            promptIndex = (promptIndex + 1) % PROMPTS.length;
            tvPrompt.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                tvPrompt.setText(PROMPTS[promptIndex]);
                tvPrompt.animate().alpha(1f).setDuration(400).start();
            }).start();
            handler.postDelayed(this, 9000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RingActivity.showOverLock(this);
        setContentView(R.layout.activity_record);

        // Si la alarma sigue sonando (se llegó desde la notificación), se apaga.
        stopService(new Intent(this, AlarmService.class));

        tvTimer = findViewById(R.id.tvTimer);
        tvPrompt = findViewById(R.id.tvPrompt);
        tvStatus = findViewById(R.id.tvStatus);
        btnPause = findViewById(R.id.btnPause);
        dot = findViewById(R.id.dot);
        halo = findViewById(R.id.halo);

        findViewById(R.id.btnSave).setOnClickListener(v -> saveAndClose());
        btnPause.setOnClickListener(v -> togglePause());
        findViewById(R.id.btnDiscard).setOnClickListener(v -> confirmDiscard());

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            tvStatus.setText("Falta permiso de micrófono");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQ_MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Ui.toast(this, "Sin permiso de micrófono no puedo grabar tu sueño");
            finish();
        }
    }

    private long elapsed() {
        long now = SystemClock.elapsedRealtime();
        long p = pausedTotal + (paused ? now - pausedAt : 0);
        return now - startElapsed - p;
    }

    private void startRecording() {
        startedAt = System.currentTimeMillis();
        outFile = new File(DreamStore.audioDir(this), DreamStore.newFileName(startedAt));
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96_000);
            recorder.setAudioSamplingRate(44_100);
            recorder.setAudioChannels(1);
            recorder.setOutputFile(outFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            Ui.toast(this, "No se pudo iniciar el micrófono");
            releaseRecorder();
            finish();
            return;
        }
        recording = true;
        startElapsed = SystemClock.elapsedRealtime();
        tvStatus.setText("● Grabando");
        handler.post(meter);
        handler.postDelayed(rotatePrompt, 9000);
    }

    private void togglePause() {
        if (!recording) return;
        try {
            if (paused) {
                recorder.resume();
                pausedTotal += SystemClock.elapsedRealtime() - pausedAt;
                paused = false;
                btnPause.setText("Pausar");
                tvStatus.setText("● Grabando");
            } else {
                recorder.pause();
                pausedAt = SystemClock.elapsedRealtime();
                paused = true;
                btnPause.setText("Seguir");
                tvStatus.setText("❚❚ En pausa");
            }
        } catch (Exception ignored) {}
    }

    /** Para la grabación y la guarda. Devuelve el sueño guardado o null. */
    private DreamStore.Dream stopAndStore() {
        if (!recording || finished) return null;
        finished = true;
        recording = false;
        long dur = elapsed();
        handler.removeCallbacksAndMessages(null);
        boolean ok = true;
        try {
            if (paused) recorder.resume();
            recorder.stop();
        } catch (Exception e) {
            ok = false;
        }
        releaseRecorder();
        if (!ok || dur < 1000) {
            outFile.delete();
            return null;
        }
        return DreamStore.add(this, outFile.getName(), startedAt, dur);
    }

    private void saveAndClose() {
        DreamStore.Dream d = stopAndStore();
        if (d == null) {
            Ui.toast(this, "La grabación era demasiado corta y no se ha guardado");
            finish();
            return;
        }
        if (RingActivity.isLocked(this)) {
            Ui.toast(this, "Sueño guardado. Ponle nombre y etiquetas cuando desbloquees.");
        } else {
            startActivity(new Intent(this, DreamDetailActivity.class)
                    .putExtra("id", d.id)
                    .putExtra("fresh", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    private void confirmDiscard() {
        new Ui.Sheet(this, "¿Descartar la grabación?")
                .message("Se borrará lo que has grabado ahora.")
                .primary("Seguir grabando", null)
                .danger("Descartar", () -> {
                    finished = true;
                    recording = false;
                    handler.removeCallbacksAndMessages(null);
                    try { recorder.stop(); } catch (Exception ignored) {}
                    releaseRecorder();
                    if (outFile != null) outFile.delete();
                    finish();
                })
                .show();
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (recording) saveAndClose();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        // Si la pantalla se cierra a mitad, no perdemos el sueño: se guarda tal cual.
        if (recording && !finished) stopAndStore();
        releaseRecorder();
        super.onDestroy();
    }
}
