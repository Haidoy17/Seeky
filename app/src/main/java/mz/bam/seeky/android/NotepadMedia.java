package mz.bam.seeky.android;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Pictures, drawings and voice recordings kept with the notes (private app storage). */
final class NotepadMedia {
    static String name(String prefix, String ext) { return prefix + "_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000) + "." + ext; }

    /** Copies a picture (gallery or camera) at most 1600 px wide as JPEG; returns its name. */
    static String image(Context c, Uri source) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(source)) { if (in == null) throw new IOException(); BitmapFactory.decodeStream(in, null, o); }
        if (o.outWidth <= 0) throw new IOException("Imagem inválida.");
        int sample = 1; while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1600) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options(); d.inSampleSize = sample;
        Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(source)) { b = BitmapFactory.decodeStream(in, null, d); }
        if (b == null) throw new IOException("Imagem inválida.");
        int rotate = 0;
        try (InputStream in = c.getContentResolver().openInputStream(source)) { if (in != null) { android.media.ExifInterface exif = new android.media.ExifInterface(in); int or = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1); rotate = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0; } } catch (Exception ignored) {}
        if (rotate != 0) { Matrix m = new Matrix(); m.postRotate(rotate); b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true); }
        float scale = Math.min(1f, 1600f / Math.max(b.getWidth(), b.getHeight()));
        if (scale < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true);
        return save(c, b, false);
    }
    static String save(Context c, Bitmap b, boolean png) throws IOException {
        String n = name(png ? "draw" : "img", png ? "png" : "jpg");
        try (OutputStream out = new FileOutputStream(NotepadStore.media(c, n))) { b.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 88, out); }
        return n;
    }
    /** Bytes, size and type of a picture for the Word export. */
    static Object[] forExport(Context c, String ref) {
        try {
            File f = NotepadStore.media(c, ref);
            if (f == null || !f.isFile()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getPath(), o);
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            return new Object[]{data, o.outWidth, o.outHeight, ref.endsWith(".png") ? "png" : "jpeg"};
        } catch (Exception e) { return null; }
    }

    // ---- drawing ----
    static final class Canvas extends View {
        static final class Stroke { final Path path = new Path(); final Paint paint; Stroke(Paint p) { paint = p; } }
        final List<Stroke> strokes = new ArrayList<>(); Stroke current; int color = 0xff222222; float width = 6; boolean eraser; final int background;
        Canvas(Context c, int background) { super(c); this.background = background; setBackgroundColor(background); }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(eraser ? background : color); p.setStrokeWidth(eraser ? width * 4 : width * getResources().getDisplayMetrics().density / 2); current = new Stroke(p); current.path.moveTo(x, y); strokes.add(current); break; }
                case MotionEvent.ACTION_MOVE: if (current != null) { for (int h = 0; h < e.getHistorySize(); h++) current.path.lineTo(e.getHistoricalX(h), e.getHistoricalY(h)); current.path.lineTo(x, y); } break;
                case MotionEvent.ACTION_UP: if (current != null) current.path.lineTo(x + .1f, y + .1f); current = null; break;
                default: break;
            }
            invalidate();
            return true;
        }
        @Override protected void onDraw(android.graphics.Canvas c) { for (Stroke s : strokes) c.drawPath(s.path, s.paint); }
        void undo() { if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1); invalidate(); }
        void clear() { strokes.clear(); invalidate(); }
        Bitmap picture() { Bitmap b = Bitmap.createBitmap(Math.max(1, getWidth()), Math.max(1, getHeight()), Bitmap.Config.ARGB_8888); android.graphics.Canvas c = new android.graphics.Canvas(b); c.drawColor(background); for (Stroke s : strokes) c.drawPath(s.path, s.paint); return b; }
    }
    /** Full-screen drawing: six colours, three pen sizes, eraser, undo and clear. */
    static void draw(Activity a, Consumer<String> saved) {
        Screens s = new Screens(a);
        Dialog d = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        LinearLayout root = s.column(); root.setBackgroundColor(0xfff7f5f0);
        Canvas canvas = new Canvas(a, 0xffffffff);
        LinearLayout top = s.row(); top.setPadding(s.dp(12), s.dp(10), s.dp(12), s.dp(10));
        TextView title = s.text("Desenho", 18, 0xff222222, true); top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView cancel = s.text("Cancelar", 15, 0xff666666, true); cancel.setPadding(s.dp(12), s.dp(8), s.dp(12), s.dp(8)); cancel.setOnClickListener(v -> d.dismiss()); top.addView(cancel);
        TextView ok = s.text("Inserir", 15, 0xffffffff, true); ok.setPadding(s.dp(16), s.dp(8), s.dp(16), s.dp(8)); ok.setBackground(s.round(0xff3d5a45, 18));
        ok.setOnClickListener(v -> { if (canvas.strokes.isEmpty()) { d.dismiss(); return; } try { saved.accept(save(a, crop(canvas.picture()), true)); } catch (IOException e) { PlusUi.toast(a, "Não foi possível guardar o desenho."); } d.dismiss(); });
        top.addView(ok);
        root.addView(top);
        root.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout tools = s.row(); tools.setPadding(s.dp(10), s.dp(8), s.dp(10), s.dp(14)); tools.setGravity(Gravity.CENTER);
        int[] colors = {0xff222222, 0xff2f6fde, 0xffd64545, 0xffe08a1a, 0xff2e9d57, 0xff8a4fd1};
        List<View> dots = new ArrayList<>();
        for (int col : colors) {
            View dot = new View(a); android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setShape(android.graphics.drawable.GradientDrawable.OVAL); g.setColor(col); g.setStroke(col == canvas.color ? s.dp(3) : 0, 0xffb08a3c); dot.setBackground(g);
            dot.setContentDescription("Cor"); dots.add(dot);
            dot.setOnClickListener(v -> { canvas.color = col; canvas.eraser = false; for (int i = 0; i < dots.size(); i++) { android.graphics.drawable.GradientDrawable x = new android.graphics.drawable.GradientDrawable(); x.setShape(android.graphics.drawable.GradientDrawable.OVAL); x.setColor(colors[i]); x.setStroke(colors[i] == col ? s.dp(3) : 0, 0xffb08a3c); dots.get(i).setBackground(x); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s.dp(30), s.dp(30)); lp.rightMargin = s.dp(8); tools.addView(dot, lp);
        }
        root.addView(tools);
        LinearLayout more = s.row(); more.setPadding(s.dp(10), 0, s.dp(10), s.dp(16)); more.setGravity(Gravity.CENTER);
        String[][] actions = {{"Fino", "4"}, {"Médio", "8"}, {"Grosso", "16"}, {"Borracha", "e"}, {"Desfazer", "u"}, {"Limpar", "c"}};
        for (String[] act : actions) {
            TextView b = s.text(act[0], 13, 0xff333333, true); b.setPadding(s.dp(10), s.dp(8), s.dp(10), s.dp(8)); b.setBackground(s.round(0xffeae6dd, 14));
            b.setOnClickListener(v -> { switch (act[1]) { case "e": canvas.eraser = true; break; case "u": canvas.undo(); break; case "c": canvas.clear(); break; default: canvas.width = Float.parseFloat(act[1]); canvas.eraser = false; } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = s.dp(6); more.addView(b, lp);
        }
        root.addView(s.scroller(more));
        d.setContentView(root);
        d.show();
    }
    /** The drawing without empty margins. */
    static Bitmap crop(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), left = w, right = -1, top = h, bottom = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y += 2) { b.getPixels(row, 0, w, 0, y, w, 1); for (int x = 0; x < w; x += 2) if ((row[x] & 0xffffff) != 0xffffff) { if (x < left) left = x; if (x > right) right = x; if (y < top) top = y; if (y > bottom) bottom = y; } }
        if (right < 0) return b;
        int pad = 24; left = Math.max(0, left - pad); top = Math.max(0, top - pad); right = Math.min(w - 1, right + pad); bottom = Math.min(h - 1, bottom + pad);
        return Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1);
    }

    // ---- voice recordings ----
    static MediaPlayer player; static String playing;
    static void play(Context c, String ref) {
        try {
            if (player != null) { boolean same = ref.equals(playing); player.release(); player = null; playing = null; if (same) return; }
            File f = NotepadStore.media(c, ref);
            if (f == null || !f.isFile()) { PlusUi.toast(c, "A gravação já não existe."); return; }
            player = new MediaPlayer(); player.setDataSource(f.getPath()); player.setOnCompletionListener(m -> { m.release(); if (player == m) { player = null; playing = null; } }); player.prepare(); player.start(); playing = ref;
            PlusUi.toast(c, "A ouvir a gravação. Toque outra vez para parar.");
        } catch (Exception e) { PlusUi.toast(c, "Não foi possível ouvir a gravação."); }
    }
    static void stop() { if (player != null) { player.release(); player = null; playing = null; } }
    /** Records a voice note; on stop gives the file name and the length. */
    static void record(Activity a, java.util.function.BiConsumer<String, Long> done) {
        if (a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { a.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 53); PlusUi.toast(a, "Autorize o microfone e toque outra vez em Gravar."); return; }
        String n = name("rec", "m4a"); File f = NotepadStore.media(a, n);
        MediaRecorder r = new MediaRecorder();
        try { r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(64000); r.setAudioSamplingRate(44100); r.setOutputFile(f.getPath()); r.prepare(); r.start(); }
        catch (Exception e) { r.release(); f.delete(); PlusUi.toast(a, "Não foi possível usar o microfone."); return; }
        long started = SystemClock.elapsedRealtime();
        Screens s = new Screens(a);
        LinearLayout box = s.column(); box.setGravity(Gravity.CENTER); box.setPadding(s.dp(10), s.dp(10), s.dp(10), s.dp(10));
        TextView time = s.text("0:00", 34, s.p.accent, true); time.setGravity(Gravity.CENTER); box.addView(time);
        TextView hint = s.text("A gravar… fale perto do telefone.", 14, s.p.muted, false); hint.setGravity(Gravity.CENTER); box.addView(hint);
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> { long sec = (SystemClock.elapsedRealtime() - started) / 1000; time.setText(sec / 60 + ":" + String.format(Locale.ROOT, "%02d", sec % 60)); PlusUi.MAIN.postDelayed(tick[0], 500); };
        PlusUi.MAIN.post(tick[0]);
        boolean[] finished = {false};
        UiDialog dialog = new UiDialog.Builder(a).setTitle("Gravação de voz").setView(box).setPositiveButton("Parar e inserir", (x, w) -> {
            finished[0] = true; PlusUi.MAIN.removeCallbacks(tick[0]); long ms = SystemClock.elapsedRealtime() - started;
            try { r.stop(); } catch (Exception e) { f.delete(); } r.release();
            if (f.isFile() && f.length() > 0) done.accept(n, ms); else PlusUi.toast(a, "A gravação ficou vazia.");
        }).setNegativeButton("Cancelar", (x, w) -> { finished[0] = true; PlusUi.MAIN.removeCallbacks(tick[0]); try { r.stop(); } catch (Exception ignored) {} r.release(); f.delete(); }).create();
        dialog.setOnDismissListener(x -> { if (!finished[0]) { PlusUi.MAIN.removeCallbacks(tick[0]); try { r.stop(); } catch (Exception ignored) {} r.release(); f.delete(); } });
        dialog.show();
    }
    private NotepadMedia() {}
}
