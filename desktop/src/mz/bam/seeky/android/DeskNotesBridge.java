package mz.bam.seeky.android;

/**
 * 5.57 · What the note editor page may ask the window (public, so the page can call it): find passages in a
 * piece of text, and hand over the note's marks after each change.
 */
public final class DeskNotesBridge {
    final java.util.function.Consumer<String> onChange;
    DeskNotesBridge(java.util.function.Consumer<String> onChange) { this.onChange = onChange; }
    /** "start\tend\ttitle" per passage, one per line ("" when none). */
    public String refs(String text) { return refsOf(text); }
    public void changed(String markup) { if (markup != null) onChange.accept(markup); }
    static String refsOf(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (BibleReferences.Reference r : BibleReferences.references(text)) { if (b.length() > 0) b.append('\n'); b.append(r.start).append('\t').append(r.end).append('\t').append(r.title.replace('\t', ' ').replace('\n', ' ')); }
        return b.toString();
    }
}
