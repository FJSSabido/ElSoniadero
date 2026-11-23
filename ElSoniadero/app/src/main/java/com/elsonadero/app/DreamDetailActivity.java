package com.elsonadero.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Ficha de un sueño: escuchar, titular, etiquetar, anotar o borrar. */
public class DreamDetailActivity extends Activity {

    private DreamStore.Dream dream;
    private MediaPlayer player;
    private boolean prepared;
    private boolean deleted;
    private TextView btnPlay, tvPos;
    private SeekBar seek;
    private EditText etTitle, etNotes;
    private FlowLayout tagFlow;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable progress = new Runnable() {
        @Override
        public void run() {
            if (player != null && prepared) {
                int p = player.getCurrentPosition();
                seek.setProgress(p);
                tvPos.setText(Ui.duration(p) + " / " + Ui.duration(player.getDuration()));
                if (player.isPlaying()) handler.postDelayed(this, 250);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dream = DreamStore.get(this, getIntent().getStringExtra("id"));
        if (dream == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_detail);

        etTitle = findViewById(R.id.etTitle);
        etNotes = findViewById(R.id.etNotes);
        btnPlay = findViewById(R.id.btnPlay);
        tvPos = findViewById(R.id.tvPos);
        seek = findViewById(R.id.seek);
        tagFlow = findViewById(R.id.tagFlow);

        etTitle.setText(dream.title);
        etNotes.setText(dream.notes);
        ((TextView) findViewById(R.id.tvDate)).setText(
                new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy · HH:mm", DreamStore.ES).format(new Date(dream.created))
                        + "  ·  " + Ui.duration(dream.duration));
        tvPos.setText("0:00 / " + Ui.duration(dream.duration));
        seek.setMax((int) Math.max(1, dream.duration));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnPlay.setOnClickListener(v -> togglePlay());
        findViewById(R.id.btnManageTags).setOnClickListener(v -> Ui.manageTags(this, this::reloadTags));
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnShare).setOnClickListener(v -> {
            saveText();
            if (player != null && player.isPlaying()) togglePlay();
            DreamShare.showOptions(this, dream);
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && player != null && prepared) {
                    player.seekTo(p);
                    tvPos.setText(Ui.duration(p) + " / " + Ui.duration(player.getDuration()));
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        renderTags();
    }

    // ---------- Reproductor ----------

    private boolean ensurePlayer() {
        if (player != null) return prepared;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(DreamStore.audioFile(this, dream).getAbsolutePath());
            player.prepare();
            prepared = true;
            seek.setMax(player.getDuration());
            player.setOnCompletionListener(mp -> {
                btnPlay.setText("▶");
                seek.setProgress(0);
                mp.seekTo(0);
                tvPos.setText("0:00 / " + Ui.duration(mp.getDuration()));
            });
        } catch (Exception e) {
            Ui.toast(this, "No encuentro el audio de este sueño");
            player = null;
            prepared = false;
        }
        return prepared;
    }

    private void togglePlay() {
        if (!ensurePlayer()) return;
        if (player.isPlaying()) {
            player.pause();
            btnPlay.setText("▶");
        } else {
            player.start();
            btnPlay.setText("❚❚");
            handler.post(progress);
        }
    }

    // ---------- Etiquetas ----------

    private void reloadTags() {
        // Recarga el sueño por si se renombró o borró alguna etiqueta.
        DreamStore.Dream fresh = DreamStore.get(this, dream.id);
        if (fresh != null) dream = fresh;
        renderTags();
    }

    private void renderTags() {
        tagFlow.removeAllViews();
        List<String> all = DreamStore.tags(this);
        for (String t : all) {
            TextView chip = Ui.chip(this, t, dream.tags.contains(t));
            chip.setOnClickListener(v -> {
                if (dream.tags.contains(t)) dream.tags.remove(t);
                else dream.tags.add(t);
                Ui.setChip(chip, dream.tags.contains(t));
                DreamStore.update(this, dream);
            });
            tagFlow.addView(chip);
        }
        TextView add = Ui.addChip(this, "+ Nueva");
        add.setOnClickListener(v -> Ui.newTag(this, name -> {
            if (!dream.tags.contains(name)) dream.tags.add(name);
            DreamStore.update(this, dream);
            renderTags();
        }));
        tagFlow.addView(add);
    }

    // ---------- Guardar / borrar ----------

    private void saveText() {
        if (dream == null || deleted) return;
        String title = etTitle.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();
        if (!title.equals(dream.title) || !notes.equals(dream.notes)) {
            dream.title = title.isEmpty() ? DreamStore.defaultTitle(dream.created) : title;
            dream.notes = notes;
            DreamStore.update(this, dream);
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar este sueño?")
                .setMessage("Se borrarán la grabación y las notas. No se puede deshacer.")
                .setPositiveButton("Borrar", (d, w) -> {
                    releasePlayer();
                    deleted = true;
                    DreamStore.delete(this, dream);
                    Ui.toast(this, "Sueño borrado");
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void releasePlayer() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
            prepared = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveText();
        if (player != null && player.isPlaying()) {
            player.pause();
            btnPlay.setText("▶");
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
