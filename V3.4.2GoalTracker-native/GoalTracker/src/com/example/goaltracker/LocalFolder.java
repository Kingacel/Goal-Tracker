package com.example.goaltracker;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional local storage folder. The user picks a location through Android's folder picker
 * (no storage permission needed); the app creates a "GoalTracker" folder there and keeps one file,
 * goal-tracker-data.json, up to date after every change. Writes run on a background thread and
 * only the newest data is written, so typing never waits on the file system.
 */
final class LocalFolder {
    private LocalFolder() {}

    static final String DIR_NAME = "GoalTracker";
    static final String FILE_NAME = "goal-tracker-data.json";
    static final int MAX_BYTES = 10 * 1024 * 1024;

    private static final String PREFS = "goal_tracker_settings";   // same file as the appearance setting
    private static final String K_TREE = "folder_tree_uri", K_FILE = "folder_file_uri",
            K_LABEL = "folder_label", K_LAST = "folder_last_sync", K_ERR = "folder_error";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final AtomicReference<String> PENDING = new AtomicReference<>();

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isSet(Context c) { return prefs(c).getString(K_TREE, null) != null; }

    /** Result of looking into a chosen folder. */
    static final class Found {
        Uri file;
        String existing = "";   // current content of goal-tracker-data.json ("" if it was just created)
        String label;
    }

    /** Text for the Settings row. */
    static String status(Context c) {
        SharedPreferences sp = prefs(c);
        if (sp.getString(K_TREE, null) == null) {
            return "Not set. Data is kept inside the app only";
        }
        String label = sp.getString(K_LABEL, DIR_NAME);
        String err = sp.getString(K_ERR, null);
        if (err != null) return label + "\nSync problem: " + err;
        long t = sp.getLong(K_LAST, 0);
        return label + (t == 0 ? "" : "\nLast synced "
                + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT).format(new java.util.Date(t)));
    }

    /** Called after every save. Does nothing unless a folder is set. */
    static void queue(Context c, String json) {
        if (json == null || !isSet(c)) return;
        final Context app = c.getApplicationContext();
        PENDING.set(json);
        EXEC.execute(() -> flush(app));
    }

    private static synchronized void flush(Context c) {
        String json = PENDING.getAndSet(null);          // newest data wins; older queued runs find null
        if (json == null) return;
        SharedPreferences sp = prefs(c);
        String t = sp.getString(K_TREE, null);
        if (t == null) return;
        Uri tree = Uri.parse(t);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Uri f = null;
                if (attempt == 0) {
                    String cached = sp.getString(K_FILE, null);
                    if (cached != null) f = Uri.parse(cached);
                }
                if (f == null) {   // first run, or the file/folder was moved or deleted: find or re-create it
                    f = findOrCreate(c.getContentResolver(), tree, new boolean[1]);
                    sp.edit().putString(K_FILE, f.toString()).apply();
                }
                try (OutputStream out = c.getContentResolver().openOutputStream(f, "wt")) {
                    if (out == null) throw new IOException("Cannot open the file");
                    out.write(json.getBytes("UTF-8"));
                    out.flush();
                }
                sp.edit().putLong(K_LAST, System.currentTimeMillis()).remove(K_ERR).apply();
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        String msg = last instanceof SecurityException
                ? "access to the folder was removed. Choose it again"
                : String.valueOf(last == null ? "unknown error" : last.getMessage());
        sp.edit().putString(K_ERR, msg).apply();
    }

    /** Finds or creates GoalTracker/goal-tracker-data.json inside the chosen folder. Blocking: use a worker thread. */
    static Found inspect(Context c, Uri tree) throws IOException {
        ContentResolver r = c.getContentResolver();
        boolean[] created = new boolean[1];
        Found f = new Found();
        f.file = findOrCreate(r, tree, created);
        if (!created[0]) {
            try (InputStream in = r.openInputStream(f.file)) {
                if (in != null) f.existing = readAll(in);
            }
        }
        String root = DocumentsContract.getTreeDocumentId(tree);
        String name = null;
        try (Cursor cur = r.query(DocumentsContract.buildDocumentUriUsingTree(tree, root),
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) name = cur.getString(0);
        } catch (Exception ignored) { }
        f.label = (name == null || name.isEmpty() ? "Selected folder" : name) + " / " + DIR_NAME;
        return f;
    }

    /** Starts syncing to the folder found by inspect(). Releases the previous folder's permission. */
    static void commit(Context c, Uri tree, Found f) {
        SharedPreferences sp = prefs(c);
        String old = sp.getString(K_TREE, null);
        if (old != null && !old.equals(tree.toString())) release(c, Uri.parse(old));
        sp.edit().putString(K_TREE, tree.toString()).putString(K_FILE, f.file.toString())
                .putString(K_LABEL, f.label).remove(K_ERR).remove(K_LAST).commit();
    }

    /** Stops syncing. Files already in the folder are left untouched. */
    static void clear(Context c) {
        SharedPreferences sp = prefs(c);
        String old = sp.getString(K_TREE, null);
        sp.edit().remove(K_TREE).remove(K_FILE).remove(K_LABEL).remove(K_LAST).remove(K_ERR).commit();
        PENDING.set(null);
        if (old != null) release(c, Uri.parse(old));
    }

    static void release(Context c, Uri tree) {
        try {
            c.getContentResolver().releasePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) { }
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("File is too large");
        }
        String s = new String(out.toByteArray(), "UTF-8");
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }

    private static Uri findOrCreate(ContentResolver r, Uri tree, boolean[] created) throws IOException {
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        Uri rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
        Uri dir = findChild(r, tree, rootId, DIR_NAME, true);
        if (dir == null) dir = DocumentsContract.createDocument(r, rootDoc,
                DocumentsContract.Document.MIME_TYPE_DIR, DIR_NAME);
        if (dir == null) throw new IOException("Could not create the " + DIR_NAME + " folder");
        Uri file = findChild(r, tree, DocumentsContract.getDocumentId(dir), FILE_NAME, false);
        if (file == null) {
            file = DocumentsContract.createDocument(r, dir, "application/json", FILE_NAME);
            created[0] = true;
        }
        if (file == null) throw new IOException("Could not create the data file");
        return file;
    }

    private static Uri findChild(ContentResolver r, Uri tree, String parentId, String name, boolean dir) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        try (Cursor cur = r.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cur == null) return null;
            while (cur.moveToNext()) {
                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(cur.getString(2));
                if (isDir == dir && name.equals(cur.getString(1))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0));
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
