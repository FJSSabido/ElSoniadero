package com.elsonadero.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    // ---------- Ventanas con el estilo de la app ----------

    /** Ventana flotante sin el marco del sistema: el contenido trae su propio fondo redondeado. */
    public static Dialog styledDialog(Activity a, View content, boolean cancelable) {
        Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(content);
        dlg.setCancelable(cancelable);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = a.getResources().getDisplayMetrics().widthPixels - 2 * dp(a, 20);
            w.setLayout(Math.min(width, dp(a, 460)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return dlg;
    }

    /** Da a un campo de texto el aspecto de la app (caja púrpura redondeada). */
    public static void styleField(EditText e) {
        Context c = e.getContext();
        e.setBackgroundResource(R.drawable.field);
        int p = dp(c, 16);
        e.setPadding(p, dp(c, 14), p, dp(c, 14));
        e.setTextColor(c.getColor(R.color.ink));
        e.setHintTextColor(c.getColor(R.color.mist));
        e.setTextSize(17);
    }

    /**
     * Ventana con el estilo de El Soñadero: título en serif cursiva amarillo, texto en bruma
     * y botones redondeados apilados. Cada botón cierra la ventana y luego ejecuta su acción.
     */
    public static final class Sheet {
        private final Activity a;
        private final LinearLayout box;
        private final Dialog dlg;
        private EditText focus;
        private int buttons = 0;

        public Sheet(Activity a, String title) {
            this.a = a;
            box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundResource(R.drawable.dialog_bg);
            box.setPadding(dp(a, 22), dp(a, 24), dp(a, 22), dp(a, 10));
            TextView t = new TextView(a);
            t.setText(title);
            t.setTextColor(a.getColor(R.color.neon));
            t.setTextSize(24);
            t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
            box.addView(t);
            dlg = styledDialog(a, box, true);
        }

        public Sheet message(String m) {
            TextView t = new TextView(a);
            t.setText(m);
            t.setTextColor(a.getColor(R.color.mist));
            t.setTextSize(16);
            t.setLineSpacing(0, 1.2f);
            add(t, 8);
            return this;
        }

        public Sheet view(View v) {
            add(v, 16);
            return this;
        }

        public Sheet field(EditText e) {
            styleField(e);
            focus = e;
            return view(e);
        }

        private void add(View v, int topDp) {
            LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) v.getLayoutParams()
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(a, topDp);
            box.addView(v, lp);
        }

        private Sheet button(String label, int bg, int color, Runnable r) {
            Button b = new Button(a);
            b.setText(label);
            b.setAllCaps(false);
            b.setBackgroundResource(bg);
            b.setTextColor(a.getColor(color));
            b.setTextSize(17);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setMinHeight(dp(a, 54));
            b.setStateListAnimator(null);
            b.setOnClickListener(v -> {
                dlg.dismiss();
                if (r != null) r.run();
            });
            add(b, buttons++ == 0 ? 22 : 10);
            return this;
        }

        public Sheet primary(String label, Runnable r) { return button(label, R.drawable.btn_primary, R.color.night, r); }

        public Sheet secondary(String label, Runnable r) { return button(label, R.drawable.btn_ghost, R.color.neon, r); }

        public Sheet danger(String label, Runnable r) { return button(label, R.drawable.btn_danger, R.color.night, r); }

        public Sheet dangerGhost(String label, Runnable r) { return button(label, R.drawable.btn_danger_ghost, R.color.rose, r); }

        public Sheet cancel(String label, Runnable r) {
            TextView t = new TextView(a);
            t.setText(label);
            t.setTextColor(a.getColor(R.color.mist));
            t.setTextSize(16);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(a, 50));
            TypedValue tv = new TypedValue();
            a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            t.setBackgroundResource(tv.resourceId);
            t.setOnClickListener(v -> {
                dlg.dismiss();
                if (r != null) r.run();
            });
            add(t, buttons == 0 ? 18 : 6);
            return this;
        }

        public Dialog show() {
            if (focus != null && dlg.getWindow() != null) {
                dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                focus.requestFocus();
            }
            dlg.show();
            return dlg;
        }
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

    /** Pide el nombre de una etiqueta nueva; onCreated recibe el nombre guardado. */
    public static void newTag(Activity a, Consumer<String> onCreated) {
        EditText e = field(a, "", "Por ejemplo: Viajes");
        new Sheet(a, "Nueva etiqueta")
                .field(e)
                .primary("Crear etiqueta", () -> {
                    String saved = DreamStore.addTag(a, e.getText().toString());
                    if (saved == null) toast(a, "Escribe un nombre para la etiqueta");
                    else if (onCreated != null) onCreated.accept(saved);
                })
                .cancel("Cancelar", null)
                .show();
    }

    /** Lista de etiquetas: tocar una para renombrarla o borrarla, o crear una nueva. */
    public static void manageTags(Activity a, Runnable onChange) {
        List<String> tags = DreamStore.tags(a);
        Sheet s = new Sheet(a, "Etiquetas");
        final Dialog[] holder = new Dialog[1];
        if (tags.isEmpty()) {
            s.message("Todavía no hay etiquetas. Crea la primera.");
        } else {
            s.message("Toca una para cambiarle el nombre o borrarla.");
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            for (String t : tags) {
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundResource(R.drawable.row_tag);
                row.setPadding(dp(a, 18), 0, dp(a, 16), 0);
                row.setMinimumHeight(dp(a, 52));
                TextView name = new TextView(a);
                name.setText(t);
                name.setTextColor(a.getColor(R.color.ink));
                name.setTextSize(17);
                row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                int n = DreamStore.countWithTag(a, t);
                TextView count = new TextView(a);
                count.setText(n == 0 ? "" : n + (n == 1 ? " sueño" : " sueños"));
                count.setTextColor(a.getColor(R.color.mist));
                count.setTextSize(14);
                row.addView(count);
                TextView arrow = new TextView(a);
                arrow.setText("  ›");
                arrow.setTextColor(a.getColor(R.color.neon));
                arrow.setTextSize(22);
                row.addView(arrow);
                row.setOnClickListener(v -> {
                    if (holder[0] != null) holder[0].dismiss();
                    editTag(a, t, onChange);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = list.getChildCount() == 0 ? 0 : dp(a, 8);
                list.addView(row, lp);
            }
            ScrollView sv = new ScrollView(a);
            sv.addView(list);
            if (tags.size() > 6) {
                sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 6 * 60)));
            }
            s.view(sv);
        }
        s.primary("+ Nueva etiqueta", () -> newTag(a, x -> {
            if (onChange != null) onChange.run();
            manageTags(a, onChange);
        }));
        s.cancel("Cerrar", null);
        holder[0] = s.show();
    }

    private static void editTag(Activity a, String tag, Runnable onChange) {
        EditText e = field(a, tag, "Nombre");
        new Sheet(a, "Editar etiqueta")
                .field(e)
                .primary("Guardar", () -> {
                    String nn = e.getText().toString().trim();
                    if (!nn.equals(tag) && !DreamStore.renameTag(a, tag, nn)) {
                        toast(a, nn.isEmpty() ? "El nombre no puede estar vacío" : "Ya existe una etiqueta con ese nombre");
                    }
                    if (onChange != null) onChange.run();
                    manageTags(a, onChange);
                })
                .dangerGhost("Borrar etiqueta", () -> confirmDeleteTag(a, tag, onChange))
                .cancel("Cancelar", () -> manageTags(a, onChange))
                .show();
    }

    private static void confirmDeleteTag(Activity a, String tag, Runnable onChange) {
        int n = DreamStore.countWithTag(a, tag);
        String msg = n == 0 ? "Ningún sueño la usa."
                : "Se quitará de " + n + (n == 1 ? " sueño" : " sueños") + ". Las grabaciones no se borran.";
        new Sheet(a, "¿Borrar «" + tag + "»?")
                .message(msg)
                .danger("Borrar", () -> {
                    DreamStore.deleteTag(a, tag);
                    if (onChange != null) onChange.run();
                    manageTags(a, onChange);
                })
                .cancel("Cancelar", () -> manageTags(a, onChange))
                .show();
    }
}
