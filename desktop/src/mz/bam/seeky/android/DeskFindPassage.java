package mz.bam.seeky.android;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.sql.SQLException;
import java.util.*;

/**
 * 5.60 · Encontrar a passagem a partir de um trecho ou de palavras simples, no Flash e na Seeky IA: the words are
 * looked up in the Bible on the computer (BibleFinder); when they are not clear enough, Seeky IA says which
 * passages they are, and the text shown is always the one of the Bible on the computer (the correct wording).
 */
final class DeskFindPassage {
    private DeskFindPassage() {}

    /** The word index of the chosen Bible (built once, in the background). */
    static BibleFinder finder(DeskMain d) throws Exception { return finder(d.store); }
    static BibleFinder finder(DeskStore store) throws Exception {
        long bible = store.bible(); if (bible == 0) return null;
        long verses = store.count("SELECT COUNT(*) FROM verses WHERE bible=?", bible); // a Bible imported again is indexed again
        return BibleFinder.of("desk:" + bible + ":" + verses, sink -> { for (Object[] r : store.rows("SELECT book,chapter,verse,text FROM verses WHERE bible=?", bible)) sink.verse(r[0] + "", ((Number) r[1]).intValue(), ((Number) r[2]).intValue(), r[3] + ""); });
    }
    static List<BibleFinder.Hit> local(DeskMain d, String words, int limit) { try { BibleFinder f = finder(d); return f == null ? Collections.emptyList() : f.find(words, limit); } catch (Exception e) { return Collections.emptyList(); } }
    /** Seeky IA: which passages these words are (runs off the window's thread). */
    static List<BibleReferences.Reference> ai(DeskMain d, String words) throws Exception {
        DeskAi.load(d.store);
        String answer = new SeekyAiClient().ask(SeekyAiCore.GEMINI, DeskAi.key(d.store), "", "Encontrar passagem", Collections.emptyList(), BibleFinder.aiPrompt(words, false), false, new SeekyAiClient.Call()).text;
        return BibleFinder.aiAnswer(answer);
    }
    /** The text of a passage in the chosen Bible ("" when it is not there). */
    static String text(DeskMain d, BibleReferences.Reference r) { try { long b = d.store.bible(); return b == 0 ? "" : d.store.passage(b, r).replaceAll("\\[\\d+\\] ", "").trim(); } catch (SQLException e) { return ""; } }

    /** Opens the Bible at the passage with its verses marked and brought into view. */
    static void openInBible(DeskMain d, BibleReferences.Reference r) {
        int chapter = r.parts.isEmpty() ? r.chapter : r.parts.get(0).chapter, verse = r.verse > 0 ? r.verse : 0, last = verse;
        if (!r.parts.isEmpty() && !r.parts.get(0).verses.isEmpty()) { int[] v = r.parts.get(0).verses.get(0); verse = v[0]; last = Math.max(v[0], v[1]); }
        d.bible.book = r.book; d.bible.chapter = chapter; d.bible.targetBook = r.book; d.bible.targetChapter = chapter; d.bible.targetFrom = verse; d.bible.targetTo = last;
        d.store.put("bible_book", r.book); d.store.put("bible_chapter", chapter + "");
        d.bringToFront(); d.open("bible");
    }

    /** One passage found: the reference, its text in the chosen Bible and what can be done with it. */
    static Node card(DeskMain d, BibleReferences.Reference r, String why) {
        String text = text(d, r);
        Label title = DeskUi.heading(r.title); Label body = DeskUi.text(text.isEmpty() ? "Esta passagem não está na Bíblia escolhida." : "«" + text + "»"); body.setWrapText(true);
        Button see = DeskUi.button("Ver passagem", true, () -> DeskPassage.show(d, r)); see.setGraphic(DeskUi.icon("book", 16));
        HBox actions = DeskUi.row(see, DeskUi.button("Abrir na Bíblia", false, () -> openInBible(d, r)), DeskUi.button("Copiar", false, () -> { javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent(); c.putString(r.title + " — " + text); javafx.scene.input.Clipboard.getSystemClipboard().setContent(c); d.status("Passagem copiada."); }));
        VBox card = DeskUi.card(DeskUi.row(title, DeskUi.grow(), DeskUi.muted(why)), body, actions); card.setPadding(new Insets(14));
        return card;
    }
}
