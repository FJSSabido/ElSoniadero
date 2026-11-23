package com.elsonadero.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Compartir un sueño: vídeo con toda la info, solo el audio o solo el texto. */
public final class DreamShare {

    private DreamShare() {}

    public static void showOptions(Activity a, DreamStore.Dream d) {
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_share, null);
        ((TextView) v.findViewById(R.id.tvDream)).setText(d.title.isEmpty() ? "Sin título" : d.title);
        Dialog dlg = Ui.styledDialog(a, v, true);
        v.findViewById(R.id.optVideo).setOnClickListener(x -> { dlg.dismiss(); shareVideo(a, d); });
        v.findViewById(R.id.optAudio).setOnClickListener(x -> { dlg.dismiss(); shareAudio(a, d); });
        v.findViewById(R.id.optText).setOnClickListener(x -> { dlg.dismiss(); shareText(a, d); });
        v.findViewById(R.id.btnCancel).setOnClickListener(x -> dlg.dismiss());
        dlg.show();
    }

    // ---------- Texto ----------

    public static String summary(DreamStore.Dream d) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌙 ").append(d.title.isEmpty() ? "Sin título" : d.title).append("\n");
        sb.append(new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", DreamStore.ES).format(new Date(d.created)))
                .append(" · ").append(Ui.duration(d.duration)).append("\n");
        if (!d.tags.isEmpty()) {
            for (String t : d.tags) sb.append("#").append(t.toLowerCase(DreamStore.ES).replace(' ', '_')).append(" ");
            sb.append("\n");
        }
        if (d.notes != null && !d.notes.isEmpty()) sb.append("\n").append(d.notes).append("\n");
        sb.append("\n— Grabado con El Soñadero");
        return sb.toString();
    }

    private static void shareText(Activity a, DreamStore.Dream d) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, d.title)
                .putExtra(Intent.EXTRA_TEXT, summary(d));
        a.startActivity(Intent.createChooser(i, "Compartir sueño"));
    }

    // ---------- Audio ----------

    private static String fileBase(DreamStore.Dream d) {
        String t = d.title.isEmpty() ? "Sueño" : d.title;
        t = t.replaceAll("[\\\\/:*?\"<>|\\n\\r]", " ").trim();
        if (t.length() > 60) t = t.substring(0, 60).trim();
        return t.isEmpty() ? "Sueño" : t;
    }

    private static void shareAudio(Activity a, DreamStore.Dream d) {
        try {
            ShareProvider.clear(a);
            File out = new File(ShareProvider.dir(a), fileBase(d) + ".m4a");
            try (InputStream in = new FileInputStream(DreamStore.audioFile(a, d));
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            send(a, out, "audio/mp4", d.title);
        } catch (Exception e) {
            Ui.toast(a, "No se pudo preparar el audio");
        }
    }

    private static void send(Activity a, File f, String mime, String text) {
        Uri uri = ShareProvider.uriFor(f);
        Intent i = new Intent(Intent.ACTION_SEND).setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri(f.getName(), uri));
        Intent chooser = Intent.createChooser(i, "Compartir sueño");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(chooser);
    }

    // ---------- Vídeo ----------

    private interface Progress { void update(float k); }

    private static final class Cancelled extends Exception {}

    private static void shareVideo(Activity a, DreamStore.Dream d) {
        int pad = Ui.dp(a, 22);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.dialog_bg);
        box.setPadding(pad, Ui.dp(a, 24), pad, Ui.dp(a, 8));
        TextView title = new TextView(a);
        title.setText("Preparando el vídeo");
        title.setTextColor(a.getColor(R.color.neon));
        title.setTextSize(24);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        TextView msg = new TextView(a);
        msg.setText("0 %");
        msg.setTextColor(a.getColor(R.color.mist));
        msg.setTextSize(15);
        msg.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 14));
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(a.getColor(R.color.neon)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(a.getColor(R.color.plum)));
        bar.setScaleY(2f);
        TextView cancelBtn = new TextView(a);
        cancelBtn.setText("Cancelar");
        cancelBtn.setTextColor(a.getColor(R.color.mist));
        cancelBtn.setTextSize(16);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setMinHeight(Ui.dp(a, 52));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 12);
        box.addView(title);
        box.addView(msg);
        box.addView(bar);
        box.addView(cancelBtn, lp);

        final boolean[] cancel = {false};
        Dialog dlg = Ui.styledDialog(a, box, false);
        cancelBtn.setOnClickListener(x -> { cancel[0] = true; msg.setText("Cancelando…"); });
        dlg.show();

        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File out = null;
            String error = null;
            try {
                ShareProvider.clear(a);
                out = new File(ShareProvider.dir(a), fileBase(d) + ".mp4");
                export(a, d, out, k -> ui.post(() -> {
                    int pct = Math.round(k * 100);
                    bar.setProgress(pct);
                    if (!cancel[0]) msg.setText(pct + " %");
                }), cancel);
            } catch (Cancelled c) {
                error = "";
            } catch (Exception e) {
                error = "No se pudo crear el vídeo. Prueba a compartir solo el audio.";
            }
            final File done = out;
            final String err = error;
            ui.post(() -> {
                try { dlg.dismiss(); } catch (Exception ignored) {}
                if (a.isFinishing()) return;
                if (err == null) send(a, done, "video/mp4", summary(d));
                else if (!err.isEmpty()) Ui.toast(a, err);
            });
        }, "export-video").start();
    }

    /** Une la tarjeta del sueño (como vídeo, 1 fotograma por segundo) con su audio, sin recomprimir el audio. */
    private static void export(Activity a, DreamStore.Dream d, File out, Progress prog, boolean[] cancel) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec enc = null;
        MediaMuxer mux = null;
        boolean muxStarted = false;
        try {
            ex.setDataSource(DreamStore.audioFile(a, d).getAbsolutePath());
            int aTrack = -1;
            MediaFormat aFmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    aTrack = i;
                    aFmt = f;
                    break;
                }
            }
            if (aTrack < 0) throw new IllegalStateException("sin audio");
            long durUs = aFmt.containsKey(MediaFormat.KEY_DURATION) ? aFmt.getLong(MediaFormat.KEY_DURATION) : d.duration * 1000;

            final int W = DreamCard.W, H = DreamCard.H;
            Bitmap base = DreamCard.renderBase(d);
            Bitmap frame = base.copy(Bitmap.Config.ARGB_8888, true);
            Canvas fc = new Canvas(frame);
            int[] argb = new int[W * H];
            byte[] y = new byte[W * H], u = new byte[W * H / 4], v = new byte[W * H / 4];
            base.getPixels(argb, 0, W, 0, 0, W, H);
            toYuv(argb, W, 0, H, y, u, v);

            MediaFormat vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            vf.setInteger(MediaFormat.KEY_BIT_RATE, 1_200_000);
            vf.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            enc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            enc.start();
            mux = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int vTrack = -1, mTrack = -1;
            int total = (int) (durUs / 1_000_000L) + 2;
            int idx = 0;
            boolean inDone = false, outDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outDone) {
                if (cancel[0]) throw new Cancelled();
                if (!inDone) {
                    int ii = enc.dequeueInputBuffer(10_000);
                    if (ii >= 0) {
                        if (idx >= total) {
                            enc.queueInputBuffer(ii, 0, 0, durUs + 1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            long pts = idx == total - 1 ? durUs : Math.min(idx * 1_000_000L, durUs);
                            DreamCard.drawProgress(fc, base, pts, durUs);
                            frame.getPixels(argb, DreamCard.BAND_Y * W, W, 0, DreamCard.BAND_Y, W, DreamCard.BAND_H);
                            toYuv(argb, W, DreamCard.BAND_Y, DreamCard.BAND_Y + DreamCard.BAND_H, y, u, v);
                            Image img = enc.getInputImage(ii);
                            writeImage(img, y, u, v, W, H);
                            enc.queueInputBuffer(ii, 0, W * H * 3 / 2, pts, 0);
                            idx++;
                            prog.update(0.95f * idx / total);
                        }
                    }
                }
                int oi = enc.dequeueOutputBuffer(info, 10_000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    vTrack = mux.addTrack(enc.getOutputFormat());
                    mTrack = mux.addTrack(aFmt);
                    mux.start();
                    muxStarted = true;
                } else if (oi >= 0) {
                    ByteBuffer ob = enc.getOutputBuffer(oi);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && muxStarted && ob != null) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        mux.writeSampleData(vTrack, ob, info);
                    }
                    enc.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                }
            }
            if (!muxStarted) throw new IllegalStateException("sin vídeo");

            // Audio original, copiado tal cual.
            ex.selectTrack(aTrack);
            ByteBuffer buf = ByteBuffer.allocate(512 * 1024);
            MediaCodec.BufferInfo ai = new MediaCodec.BufferInfo();
            while (true) {
                if (cancel[0]) throw new Cancelled();
                int n = ex.readSampleData(buf, 0);
                if (n < 0) break;
                ai.offset = 0;
                ai.size = n;
                ai.presentationTimeUs = ex.getSampleTime();
                ai.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                mux.writeSampleData(mTrack, buf, ai);
                ex.advance();
            }
            prog.update(1f);
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            if (enc != null) { try { enc.stop(); } catch (Exception ignored) {} enc.release(); }
            if (mux != null) { try { if (muxStarted) mux.stop(); } catch (Exception ignored) {} try { mux.release(); } catch (Exception ignored) {} }
            ex.release();
        }
    }

    /** ARGB → YUV 4:2:0 (BT.601) para las filas [y0, y1). y0 y y1 deben ser pares. */
    private static void toYuv(int[] argb, int w, int y0, int y1, byte[] Y, byte[] U, byte[] V) {
        int cw = w / 2;
        for (int j = y0; j < y1; j++) {
            int row = j * w;
            for (int i = 0; i < w; i++) {
                int c = argb[row + i];
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                Y[row + i] = (byte) (((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                if ((j & 1) == 0 && (i & 1) == 0) {
                    int k = (j / 2) * cw + i / 2;
                    U[k] = (byte) (((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                    V[k] = (byte) (((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
                }
            }
        }
    }

    private static void writeImage(Image img, byte[] y, byte[] u, byte[] v, int w, int h) {
        Image.Plane[] p = img.getPlanes();
        writePlane(p[0], y, w, h);
        writePlane(p[1], u, w / 2, h / 2);
        writePlane(p[2], v, w / 2, h / 2);
    }

    private static void writePlane(Image.Plane pl, byte[] src, int w, int h) {
        ByteBuffer b = pl.getBuffer();
        int rs = pl.getRowStride(), ps = pl.getPixelStride();
        if (ps == 1) {
            for (int r = 0; r < h; r++) {
                b.position(r * rs);
                b.put(src, r * w, w);
            }
        } else {
            for (int r = 0; r < h; r++) {
                int base = r * rs, srow = r * w;
                for (int c = 0; c < w; c++) b.put(base + c * ps, src[srow + c]);
            }
        }
    }
}
