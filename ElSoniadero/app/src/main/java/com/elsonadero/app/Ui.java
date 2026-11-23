package com.elsonadero.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/** Utilidades de interfaz compartidas: chips, formatos y el editor de etiquetas. */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(DreamStore.ES, "%d:%02d", s / 60, s % 60);
    }

    public static String time(int h, int m) {
        return String.format(DreamStore.ES, "%d:%02d", h, m);
    }

    /** "lun 23 nov · 07:31" */
    public static String dreamDate(long when) {
        return new SimpleDateFormat("EEE d MMM · HH:mm", DreamStore.ES).format(new Date(when)).replace(".", "");
    }

    /** "hoy a las 7:30", "mañana a las 7:30", "el jueves a las 7:30". */
    public static String relativeAlarm(long when) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(when);
        String hm = time(t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE));
        int diffDays = daysBetween(now, t);
        if (diffDays == 0) return "hoy a las " + hm;
        if (diffDays == 1) return "mañana a las " + hm;
        return "el " + new SimpleDateFormat("EEEE", DreamStore.ES).format(new Date(when)) + " a las " + hm;
    }

    private static int daysBetween(Calendar a, Calendar b) {
        Calendar x = (Calendar) a.clone();
        Calendar y = (Calendar) b.clone();
        for (Calendar c : new Calendar[]{x, y}) {
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        }
        return (int) Math.round((y.getTimeInMillis() - x.getTimeInMillis()) / 86_400_000.0);
    }

    public static String inHowLong(long when) {
        long min = Math.max(1, (when - System.currentTimeMillis() + 59_999) / 60_000);
        long h = min / 60, m = min % 60;
        if (h == 0) return "Sonará en " + m + " min";
        return "Sonará en " + h + " h " + m + " min";
    }

    private static final String[] DAY_SHORT = {"L", "M", "X", "J", "V", "S", "D"};
    private static final String[] DAY_NAME = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};

    public static String dayShort(int i) { return DAY_SHORT[i]; }

    public static String daysSummary(int days) {
        if (days == 0) return "Una vez";
        if (days == 0b1111111) return "Todos los días";
        if (days == 0b0011111) return "Lunes a viernes";
        if (days == 0b1100000) return "Fines de semana";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(DAY_NAME[i]);
            }
        }
        return sb.toString();
    }

    // ---------- Chips ----------

    public static TextView chip(Context c, String text, boolean on) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 34));
        t.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        setChip(t, on);
        return t;
    }

    public static void setChip(TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.chip_on : R.drawable.chip_off);
        t.setTextColor(t.getContext().getColor(on ? R.color.night : R.color.ink));
        t.setSelected(on);
    }

    public static TextView addChip(Context c, String text) {
        TextView t = chip(c, text, false);
        t.setBackgroundResource(R.drawable.chip_dashed);
        t.setTextColor(c.getColor(R.color.neon));
        return t;
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- Editor de etiquetas ----------

    private static EditText field(Activity a, String value, String hint) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setHint(hint);
        e.setText(value);
        e.setSelection(e.getText().length());
        return e;
    }

    private static View padded(Activity a, View v) {
        FrameLayout f = new FrameLayout(a);
        int p = dp(a, 22);
        f.setPadding(p, dp(a, 8), p, 0);
        f.addView(v);
        return f;
    }

    private static void showWithKeyboard(AlertDialog d) {
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }

    /** Pide el nombre de una etiqueta nueva; onCreated recibe el nombre guardado. */
    public static void newTag(Activity a, Consumer<String> onCreated) {
        EditText e = field(a, "", "Por ejemplo: Viajes");
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("Nueva etiqueta")
                .setView(padded(a, e))
                .setPositiveButton("Crear", (di, w) -> {
                    String saved = DreamStore.addTag(a, e.getText().toString());
                    if (saved == null) toast(a, "Escribe un nombre para la etiqueta");
                    else if (onCreated != null) onCreated.accept(saved);
                })
                .setNegativeButton("Cancelar", null)
                .create();
        showWithKeyboard(d);
    }

    /** Lista de etiquetas: tocar una para renombrarla o borrarla, o crear una nueva. */
    public static void manageTags(Activity a, Runnable onChange) {
        List<String> tags = DreamStore.tags(a);
        String[] items = new String[tags.size()];
        for (int i = 0; i < tags.size(); i++) {
            int n = DreamStore.countWithTag(a, tags.get(i));
            items[i] = tags.get(i) + (n > 0 ? "   ·  " + n : "");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle("Etiquetas")
                .setPositiveButton("+ Nueva etiqueta", (di, w) -> newTag(a, s -> {
                    if (onChange != null) onChange.run();
                    manageTags(a, onChange);
                }))
                .setNegativeButton("Cerrar", null);
        if (items.length == 0) b.setMessage("Todavía no hay etiquetas.");
        else b.setItems(items, (di, i) -> editTag(a, tags.get(i), onChange));
        b.show();
    }

    private static void editTag(Activity a, String tag, Runnable onChange) {
        EditText e = field(a, tag, "Nombre");
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("Editar etiqueta")
                .setView(padded(a, e))
                .setPositiveButton("Guardar", (di, w) -> {
                    String nn = e.getText().toString().trim();
                    if (!nn.equals(tag) && !DreamStore.renameTag(a, tag, nn)) {
                        toast(a, nn.isEmpty() ? "El nombre no puede estar vacío" : "Ya existe una etiqueta con ese nombre");
                    }
                    if (onChange != null) onChange.run();
                    manageTags(a, onChange);
                })
                .setNeutralButton("Borrar", (di, w) -> confirmDeleteTag(a, tag, onChange))
                .setNegativeButton("Cancelar", (di, w) -> manageTags(a, onChange))
                .create();
        showWithKeyboard(d);
    }

    private static void confirmDeleteTag(Activity a, String tag, Runnable onChange) {
        int n = DreamStore.countWithTag(a, tag);
        String msg = n == 0 ? "No la usa ningún sueño."
                : "Se quitará de " + n + (n == 1 ? " sueño" : " sueños") + ". Las grabaciones no se borran.";
        new AlertDialog.Builder(a)
                .setTitle("¿Borrar «" + tag + "»?")
                .setMessage(msg)
                .setPositiveButton("Borrar", (di, w) -> {
                    DreamStore.deleteTag(a, tag);
                    if (onChange != null) onChange.run();
                    manageTags(a, onChange);
                })
                .setNegativeButton("Cancelar", (di, w) -> manageTags(a, onChange))
                .show();
    }
}
