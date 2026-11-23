package com.elsonadero.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Sueños (audio + título, notas, etiquetas) y lista de etiquetas, en un JSON privado de la app. */
public final class DreamStore {

    public static final class Dream {
        public String id;
        public String file;
        public long created;
        public long duration;
        public String title = "";
        public String notes = "";
        public List<String> tags = new ArrayList<>();

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("file", file);
            o.put("created", created);
            o.put("duration", duration);
            o.put("title", title);
            o.put("notes", notes);
            o.put("tags", new JSONArray(tags));
            return o;
        }

        static Dream fromJson(JSONObject o) {
            Dream d = new Dream();
            d.id = o.optString("id");
            d.file = o.optString("file");
            d.created = o.optLong("created");
            d.duration = o.optLong("duration");
            d.title = o.optString("title");
            d.notes = o.optString("notes");
            JSONArray t = o.optJSONArray("tags");
            if (t != null) for (int i = 0; i < t.length(); i++) d.tags.add(t.optString(i));
            return d;
        }
    }

    public static final Locale ES = new Locale("es", "ES");
    private static final List<String> DEFAULT_TAGS = Arrays.asList(
            "Lúcido", "Pesadilla", "Recurrente", "Volar", "Agua", "Familia");

    private static List<Dream> dreams;
    private static List<String> tags;

    private DreamStore() {}

    public static File audioDir(Context c) {
        File d = new File(c.getFilesDir(), "suenos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File audioFile(Context c, Dream d) {
        return new File(audioDir(c), d.file);
    }

    private static File dbFile(Context c) {
        return new File(c.getFilesDir(), "suenos.json");
    }

    private static synchronized void ensure(Context c) {
        if (dreams != null) return;
        dreams = new ArrayList<>();
        tags = new ArrayList<>();
        File f = dbFile(c);
        if (!f.exists()) {
            tags.addAll(DEFAULT_TAGS);
            return;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            JSONObject root = new JSONObject(new String(buf, 0, off, StandardCharsets.UTF_8));
            JSONArray ds = root.optJSONArray("dreams");
            if (ds != null) for (int i = 0; i < ds.length(); i++) dreams.add(Dream.fromJson(ds.getJSONObject(i)));
            JSONArray ts = root.optJSONArray("tags");
            if (ts != null) for (int i = 0; i < ts.length(); i++) tags.add(ts.optString(i));
        } catch (Exception e) {
            if (tags.isEmpty()) tags.addAll(DEFAULT_TAGS);
        }
    }

    private static synchronized void persist(Context c) {
        try {
            JSONObject root = new JSONObject();
            JSONArray ds = new JSONArray();
            for (Dream d : dreams) ds.put(d.toJson());
            root.put("dreams", ds);
            root.put("tags", new JSONArray(tags));
            File tmp = new File(c.getFilesDir(), "suenos.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            File target = dbFile(c);
            if (!tmp.renameTo(target)) {
                target.delete();
                tmp.renameTo(target);
            }
        } catch (Exception ignored) {}
    }

    // ---------- Sueños ----------

    /** Todos los sueños, del más reciente al más antiguo. */
    public static synchronized List<Dream> all(Context c) {
        ensure(c);
        List<Dream> out = new ArrayList<>(dreams);
        out.sort((a, b) -> Long.compare(b.created, a.created));
        return out;
    }

    public static synchronized Dream get(Context c, String id) {
        ensure(c);
        for (Dream d : dreams) if (d.id.equals(id)) return d;
        return null;
    }

    /** Nombre de archivo para una grabación nueva. */
    public static String newFileName(long when) {
        return "sueno_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(when)) + ".m4a";
    }

    public static String defaultTitle(long when) {
        String day = new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(new Date(when));
        return "Sueño del " + day;
    }

    public static synchronized Dream add(Context c, String file, long created, long duration) {
        ensure(c);
        Dream d = new Dream();
        d.id = file.replace(".m4a", "");
        d.file = file;
        d.created = created;
        d.duration = duration;
        d.title = defaultTitle(created);
        dreams.add(d);
        persist(c);
        return d;
    }

    public static synchronized void update(Context c, Dream d) {
        ensure(c);
        persist(c);
    }

    public static synchronized void delete(Context c, Dream d) {
        ensure(c);
        audioFile(c, d).delete();
        dreams.removeIf(x -> x.id.equals(d.id));
        persist(c);
    }

    // ---------- Etiquetas ----------

    public static synchronized List<String> tags(Context c) {
        ensure(c);
        return new ArrayList<>(tags);
    }

    private static int indexOfTag(String name) {
        for (int i = 0; i < tags.size(); i++) if (tags.get(i).equalsIgnoreCase(name)) return i;
        return -1;
    }

    /** Devuelve la etiqueta guardada (o la existente con ese nombre), o null si el nombre está vacío. */
    public static synchronized String addTag(Context c, String name) {
        ensure(c);
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) return null;
        int i = indexOfTag(name);
        if (i >= 0) return tags.get(i);
        tags.add(name);
        persist(c);
        return name;
    }

    /** Renombra en la lista y en todos los sueños. false si el nombre nuevo ya existe o está vacío. */
    public static synchronized boolean renameTag(Context c, String oldName, String newName) {
        ensure(c);
        newName = newName == null ? "" : newName.trim();
        if (newName.isEmpty()) return false;
        int existing = indexOfTag(newName);
        int i = tags.indexOf(oldName);
        if (i < 0) return false;
        if (existing >= 0 && existing != i) return false;
        tags.set(i, newName);
        for (Dream d : dreams) {
            int j = d.tags.indexOf(oldName);
            if (j >= 0) d.tags.set(j, newName);
        }
        persist(c);
        return true;
    }

    public static synchronized void deleteTag(Context c, String name) {
        ensure(c);
        tags.remove(name);
        for (Dream d : dreams) d.tags.remove(name);
        persist(c);
    }

    public static synchronized int countWithTag(Context c, String name) {
        ensure(c);
        int n = 0;
        for (Dream d : dreams) if (d.tags.contains(name)) n++;
        return n;
    }
}
