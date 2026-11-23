package com.elsonadero.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/** Dibuja la tarjeta del sueño (720×1280) que se ve en el vídeo compartido. */
public final class DreamCard {

    public static final int W = 720, H = 1280;
    /** Franja inferior que cambia en cada fotograma (barra de progreso). Filas pares. */
    public static final int BAND_Y = 1100, BAND_H = 96;

    static final int NIGHT = 0xFF140824, DUSK = 0xFF2A1450, VIOLET = 0xFF8B5CF6;
    static final int NEON = 0xFFE4FF1A, INK = 0xFFF2EAFF, MIST = 0xFFB9A6D9, PLUM = 0xFF3B1D6E;

    private DreamCard() {}

    /** Parte fija de la tarjeta (sin la barra de progreso). */
    public static Bitmap renderBase(DreamStore.Dream d) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setShader(new LinearGradient(0, 0, 0, H, NIGHT, DUSK, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W / 2f, H * 0.55f, W * 0.8f, 0x558B5CF6, 0x008B5CF6, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);

        Random r = new Random(d.created);
        for (int i = 0; i < 70; i++) {
            p.setColor(INK);
            p.setAlpha(40 + r.nextInt(140));
            c.drawCircle(r.nextInt(W), r.nextInt(H), 1 + r.nextInt(3) * 0.8f, p);
        }
        p.setAlpha(255);

        // Luna + nombre de la app
        Path moon = new Path();
        moon.addCircle(84, 104, 30, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(98, 92, 25, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        p.setColor(NEON);
        c.drawPath(moon, p);
        TextPaint brand = text(36, NEON, Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        c.drawText("El Soñadero", 130, 118, brand);

        int x = 60, width = W - 2 * x;
        float y = 210;

        String title = d.title == null || d.title.isEmpty() ? "Sin título" : d.title;
        StaticLayout tl = layout(title, text(60, INK, Typeface.create(Typeface.SERIF, Typeface.NORMAL)), width, 4, 1.1f);
        y = draw(c, tl, x, y) + 22;

        String date = new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy · HH:mm", DreamStore.ES).format(new Date(d.created));
        TextPaint dp = text(28, MIST, Typeface.DEFAULT);
        c.drawText(date + "  ·  " + Ui.duration(d.duration), x, y + 28, dp);
        y += 70;

        // Etiquetas
        if (!d.tags.isEmpty()) {
            TextPaint tp = text(27, NIGHT, Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
            chip.setColor(NEON);
            float cx = x, ch = 50;
            for (String t : d.tags) {
                float tw = tp.measureText(t) + 40;
                if (cx > x && cx + tw > x + width) {
                    cx = x;
                    y += ch + 12;
                }
                c.drawRoundRect(new RectF(cx, y, cx + tw, y + ch), ch / 2, ch / 2, chip);
                c.drawText(t, cx + 20, y + 35, tp);
                cx += tw + 12;
            }
            y += ch + 40;
        }

        // Notas
        if (d.notes != null && !d.notes.isEmpty()) {
            TextPaint np = text(32, INK, Typeface.DEFAULT);
            float lineH = 32 * 1.35f;
            int maxLines = Math.max(1, (int) ((BAND_Y - 40 - y - 48) / lineH));
            Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
            box.setColor(0x663B1D6E);
            StaticLayout nl = layout(d.notes, np, width - 56, maxLines, 1.35f);
            c.drawRoundRect(new RectF(x, y, x + width, y + nl.getHeight() + 48), 28, 28, box);
            draw(c, nl, x + 28, y + 24);
        }
        return b;
    }

    /** Repinta la franja inferior con la barra de progreso para el instante "posUs". */
    public static void drawProgress(Canvas c, Bitmap base, long posUs, long durUs) {
        Rect band = new Rect(0, BAND_Y, W, BAND_Y + BAND_H);
        c.drawBitmap(base, band, band, null);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x0 = 60, x1 = W - 60, yb = BAND_Y + 24;
        float k = durUs <= 0 ? 0 : Math.min(1f, posUs / (float) durUs);
        p.setColor(PLUM);
        c.drawRoundRect(new RectF(x0, yb - 4, x1, yb + 4), 4, 4, p);
        p.setColor(NEON);
        float xe = x0 + (x1 - x0) * k;
        c.drawRoundRect(new RectF(x0, yb - 4, Math.max(x0 + 8, xe), yb + 4), 4, 4, p);
        c.drawCircle(xe, yb, 11, p);
        TextPaint tp = text(26, MIST, Typeface.DEFAULT);
        c.drawText(Ui.duration(posUs / 1000) + " / " + Ui.duration(durUs / 1000), x0, yb + 50, tp);
        tp.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Grabado con El Soñadero", x1, yb + 50, tp);
    }

    private static TextPaint text(float size, int color, Typeface tf) {
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(size);
        t.setColor(color);
        t.setTypeface(tf);
        return t;
    }

    private static StaticLayout layout(String s, TextPaint tp, int width, int maxLines, float spacing) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), tp, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, spacing)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private static float draw(Canvas c, StaticLayout l, float x, float y) {
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
        return y + l.getHeight();
    }
}
