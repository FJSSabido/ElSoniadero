package com.elsonadero.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 11;

    private TextView tabAlarms, tabDreams, tvNext, tvBanner;
    private View paneAlarms, paneDreams;
    private LinearLayout filterRow;
    private EditText etSearch;
    private String filterTag = null;
    private boolean showingDreams = false;

    private final List<AlarmStore.Alarm> alarms = new ArrayList<>();
    private final List<DreamStore.Dream> dreams = new ArrayList<>();
    private AlarmAdapter alarmAdapter;
    private DreamAdapter dreamAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tabAlarms = findViewById(R.id.tabAlarms);
        tabDreams = findViewById(R.id.tabDreams);
        tvNext = findViewById(R.id.tvNext);
        tvBanner = findViewById(R.id.tvBanner);
        paneAlarms = findViewById(R.id.paneAlarms);
        paneDreams = findViewById(R.id.paneDreams);
        filterRow = findViewById(R.id.filterRow);
        etSearch = findViewById(R.id.etSearch);

        ListView lvAlarms = findViewById(R.id.lvAlarms);
        alarmAdapter = new AlarmAdapter();
        lvAlarms.setAdapter(alarmAdapter);
        lvAlarms.setEmptyView(findViewById(R.id.emptyAlarms));
        lvAlarms.setOnItemClickListener((p, v, pos, id) -> editAlarm(alarms.get(pos)));

        ListView lvDreams = findViewById(R.id.lvDreams);
        dreamAdapter = new DreamAdapter();
        lvDreams.setAdapter(dreamAdapter);
        lvDreams.setEmptyView(findViewById(R.id.emptyDreams));
        lvDreams.setOnItemClickListener((p, v, pos, id) ->
                startActivity(new Intent(this, DreamDetailActivity.class).putExtra("id", dreams.get(pos).id)));

        tabAlarms.setOnClickListener(v -> showTab(false));
        tabDreams.setOnClickListener(v -> showTab(true));
        findViewById(R.id.btnAddAlarm).setOnClickListener(v -> editAlarm(null));
        findViewById(R.id.btnRecordNow).setOnClickListener(v ->
                startActivity(new Intent(this, RecordActivity.class)));
        findViewById(R.id.btnTags).setOnClickListener(v -> Ui.manageTags(this, this::refreshDreams));

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { refreshDreams(); }
        });

        // Por si el sistema borró las alarmas (actualización de la app, "forzar detención"…).
        AlarmScheduler.rescheduleAll(this);

        if (savedInstanceState != null) showingDreams = savedInstanceState.getBoolean("dreams");
        showTab(showingDreams);
        askPermissions();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("dreams", showingDreams);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAlarms();
        refreshDreams();
        checkBanner();
    }

    private void showTab(boolean dreamsTab) {
        showingDreams = dreamsTab;
        paneAlarms.setVisibility(dreamsTab ? View.GONE : View.VISIBLE);
        paneDreams.setVisibility(dreamsTab ? View.VISIBLE : View.GONE);
        styleTab(tabAlarms, !dreamsTab);
        styleTab(tabDreams, dreamsTab);
    }

    private void styleTab(TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.chip_on : 0);
        t.setTextColor(getColor(on ? R.color.night : R.color.mist));
    }

    // ---------- Permisos ----------

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        checkBanner();
    }

    private void checkBanner() {
        String msg = null;
        View.OnClickListener action = null;
        NotificationManager nm = getSystemService(NotificationManager.class);
        AlarmManager am = getSystemService(AlarmManager.class);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            msg = "Activa las notificaciones para que la alarma pueda sonar. Toca aquí.";
            action = v -> openAppSettings();
        } else if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            msg = "Permite «pantalla completa» para que la alarma aparezca con el móvil bloqueado. Toca aquí.";
            action = v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) { openAppSettings(); }
            };
        } else if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            msg = "Permite «alarmas y recordatorios» para que suene a la hora exacta. Toca aquí.";
            action = v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) { openAppSettings(); }
            };
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            msg = "Permite el micrófono para poder grabar tus sueños. Toca aquí.";
            action = v -> openAppSettings();
        }
        tvBanner.setVisibility(msg == null ? View.GONE : View.VISIBLE);
        tvBanner.setText(msg);
        tvBanner.setOnClickListener(action);
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    // ---------- Alarmas ----------

    private void refreshAlarms() {
        alarms.clear();
        alarms.addAll(AlarmStore.list(this));
        alarmAdapter.notifyDataSetChanged();
        long next = AlarmScheduler.nextOverall(this);
        tvNext.setText(next < 0 ? "Sin alarmas activas" : "Próxima alarma: " + Ui.relativeAlarm(next));
    }

    private void editAlarm(AlarmStore.Alarm existing) {
        final AlarmStore.Alarm a = existing != null ? existing : new AlarmStore.Alarm();
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_alarm, null);
        TimePicker tp = v.findViewById(R.id.timePicker);
        tp.setIs24HourView(true);
        tp.setHour(a.hour);
        tp.setMinute(a.minute);

        EditText etLabel = v.findViewById(R.id.etLabel);
        etLabel.setText(a.label);
        TextView hint = v.findViewById(R.id.tvRepeatHint);

        final int[] mask = {a.days};
        LinearLayout dayRow = v.findViewById(R.id.dayRow);
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView chip = Ui.chip(this, Ui.dayShort(i), (mask[0] & bit) != 0);
            chip.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(c -> {
                mask[0] ^= bit;
                Ui.setChip(chip, (mask[0] & bit) != 0);
                hint.setText(Ui.daysSummary(mask[0]));
            });
            dayRow.addView(chip);
        }
        hint.setText(Ui.daysSummary(mask[0]));

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nueva alarma" : "Editar alarma")
                .setView(v)
                .setPositiveButton("Guardar", (d, w) -> {
                    a.hour = tp.getHour();
                    a.minute = tp.getMinute();
                    a.days = mask[0];
                    a.label = etLabel.getText().toString().trim();
                    a.enabled = true;
                    AlarmStore.save(this, a);
                    AlarmScheduler.schedule(this, a);
                    Ui.toast(this, Ui.inHowLong(a.nextTrigger(System.currentTimeMillis())));
                    refreshAlarms();
                })
                .setNegativeButton("Cancelar", null);
        if (existing != null) {
            b.setNeutralButton("Borrar", (d, w) -> {
                AlarmScheduler.cancel(this, a.id);
                AlarmStore.delete(this, a.id);
                refreshAlarms();
            });
        }
        b.show();
    }

    private class AlarmAdapter extends BaseAdapter {
        public int getCount() { return alarms.size(); }
        public Object getItem(int i) { return alarms.get(i); }
        public long getItemId(int i) { return alarms.get(i).id; }

        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_alarm, parent, false);
            AlarmStore.Alarm a = alarms.get(pos);
            TextView tvTime = v.findViewById(R.id.tvTime);
            TextView tvDays = v.findViewById(R.id.tvDays);
            Switch sw = v.findViewById(R.id.swEnabled);

            tvTime.setText(Ui.time(a.hour, a.minute));
            tvTime.setTextColor(getColor(a.enabled ? R.color.ink : R.color.mist));
            String days = Ui.daysSummary(a.days);
            tvDays.setText(a.label.isEmpty() ? days : days + " · " + a.label);

            sw.setOnCheckedChangeListener(null);
            sw.setChecked(a.enabled);
            sw.setOnCheckedChangeListener((btn, checked) -> {
                a.enabled = checked;
                AlarmStore.save(MainActivity.this, a);
                AlarmScheduler.schedule(MainActivity.this, a);
                if (checked) Ui.toast(MainActivity.this, Ui.inHowLong(a.nextTrigger(System.currentTimeMillis())));
                refreshAlarms();
            });
            return v;
        }
    }

    // ---------- Sueños ----------

    private void refreshDreams() {
        List<String> tags = DreamStore.tags(this);
        if (filterTag != null && !tags.contains(filterTag)) filterTag = null;

        String q = etSearch.getText().toString().trim().toLowerCase(DreamStore.ES);
        dreams.clear();
        for (DreamStore.Dream d : DreamStore.all(this)) {
            if (filterTag != null && !d.tags.contains(filterTag)) continue;
            if (!q.isEmpty()) {
                String hay = (d.title + " " + d.notes + " " + String.join(" ", d.tags)).toLowerCase(DreamStore.ES);
                if (!hay.contains(q)) continue;
            }
            dreams.add(d);
        }
        dreamAdapter.notifyDataSetChanged();

        TextView empty = findViewById(R.id.emptyDreams);
        boolean none = DreamStore.all(this).isEmpty();
        empty.setText(none
                ? "Aquí aparecerán tus sueños grabados.\nAl apagar la alarma empezará a grabar, o pulsa «Grabar ahora»."
                : "Ningún sueño coincide con la búsqueda.");

        filterRow.removeAllViews();
        addFilterChip("Todos", null);
        for (String t : tags) addFilterChip(t, t);
    }

    private void addFilterChip(String text, String tag) {
        boolean on = (tag == null && filterTag == null) || (tag != null && tag.equals(filterTag));
        TextView chip = Ui.chip(this, text, on);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(this, 8));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            filterTag = tag;
            refreshDreams();
        });
        filterRow.addView(chip);
    }

    private class DreamAdapter extends BaseAdapter {
        public int getCount() { return dreams.size(); }
        public Object getItem(int i) { return dreams.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_dream, parent, false);
            DreamStore.Dream d = dreams.get(pos);
            ((TextView) v.findViewById(R.id.tvTitle)).setText(d.title.isEmpty() ? "Sin título" : d.title);
            ((TextView) v.findViewById(R.id.tvMeta)).setText(Ui.dreamDate(d.created) + "  ·  " + Ui.duration(d.duration));
            TextView notes = v.findViewById(R.id.tvNotes);
            notes.setText(d.notes);
            notes.setVisibility(d.notes.isEmpty() ? View.GONE : View.VISIBLE);
            TextView tags = v.findViewById(R.id.tvTags);
            StringBuilder sb = new StringBuilder();
            for (String t : d.tags) sb.append("#").append(t.toLowerCase(Locale.ROOT).replace(' ', '_')).append("  ");
            tags.setText(sb.toString().trim());
            tags.setVisibility(d.tags.isEmpty() ? View.GONE : View.VISIBLE);
            return v;
        }
    }
}
