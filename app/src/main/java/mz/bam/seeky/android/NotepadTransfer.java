package mz.bam.seeky.android;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Bloco de notas ↔ Seeky para Windows: exporta e importa todas as notas num ficheiro ".seekynotes". */
final class NotepadTransfer {
    private NotepadTransfer() {}
    static final class Result { int added, skipped, locked; }

    /** Every note except the locked ones (those stay only on this phone). */
    static byte[] export(Context c, Result result) throws Exception {
        List<NotesTransfer.Item> items = new ArrayList<>();
        for (NotepadStore.Note n : NotepadStore.all(c)) {
            if (n.locked) { result.locked++; continue; }
            NotesTransfer.Item i = new NotesTransfer.Item();
            i.title = n.title; i.body = n.body; i.folder = n.folder; i.source = n.book(); i.tags = n.tags; i.created = n.created; i.updated = n.updated; i.color = n.color; i.spacing = n.spacing; i.pinned = n.pinned;
            items.add(i);
        }
        result.added = items.size();
        return NotesTransfer.write(items, name -> { File f = NotepadStore.media(c, name); try { return f != null && f.isFile() && f.length() <= NotesTransfer.MAX_MEDIA ? Files.readAllBytes(f.toPath()) : null; } catch (IOException e) { return null; } }, "android");
    }

    static Result restore(Context c, byte[] data) throws Exception {
        NotesTransfer.Parsed parsed = NotesTransfer.read(data);
        Result r = new Result(); Set<String> have = new HashSet<>();
        for (NotepadStore.Note n : NotepadStore.all(c)) have.add(NotesTransfer.key(n.title, n.body));
        Set<String> taken = new HashSet<>(); String[] names = NotepadStore.media(c).list(); if (names != null) taken.addAll(Arrays.asList(names));
        Map<String, String> renamed = new HashMap<>();
        for (NotesTransfer.Item i : parsed.notes) {
            if (!have.add(NotesTransfer.key(i.title, i.body))) { r.skipped++; continue; }
            String body = i.body;
            for (String name : NotesTransfer.media(body)) {
                byte[] bytes = parsed.media.get(name); if (bytes == null) continue;
                String target = renamed.get(name);
                if (target == null) {
                    File f = NotepadStore.media(c, name); if (f == null) continue;
                    if (f.isFile() && !Arrays.equals(Files.readAllBytes(f.toPath()), bytes)) { target = NotesTransfer.fresh(name, taken); f = NotepadStore.media(c, target); if (f == null) continue; }
                    else target = name;
                    if (!f.isFile()) Files.write(f.toPath(), bytes);
                    taken.add(target); renamed.put(name, target);
                }
                if (!target.equals(name)) body = NotesTransfer.rename(body, name, target);
            }
            NotepadStore.Note n = new NotepadStore.Note();
            n.title = i.title; n.body = body; n.folder = i.folder; n.sourceTitle = i.source; n.tags = i.tags; n.created = i.created; n.color = i.color; n.spacing = i.spacing; n.pinned = i.pinned;
            NotepadStore.save(c, n); r.added++;
        }
        return r;
    }
}
