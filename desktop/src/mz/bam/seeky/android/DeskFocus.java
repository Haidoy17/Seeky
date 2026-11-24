package mz.bam.seeky.android;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import java.sql.SQLException;
import java.util.*;

/**
 * Foco: o Pomodoro do Seeky no computador (25 + 5, 50 + 10 ou 15 + 3 minutos, pausa longa depois de
 * quatro rondas), ligado a disciplinas, com o tempo de cada dia e de cada disciplina na semana. O
 * temporizador continua enquanto usa o resto do Seeky e aparece na barra da janela.
 */
final class DeskFocus {
    static final int[][] PATTERNS = {{25, 5, 15}, {50, 10, 20}, {15, 3, 10}};
    static final String[] LABELS = {"Clássico 25 + 5 min", "Longo 50 + 10 min", "Curto 15 + 3 min"};
    static final int ROUNDS = 4;
    final DeskMain d;
    final Timeline tick = new Timeline(new KeyFrame(Duration.seconds(1), e -> second()));
    /** phase: 0 foco, 1 pausa curta, 2 pausa longa */
    int pattern, phase, round = 1, left, total; boolean running, started; String subject = ""; long focusStart;
    final Label clock = new Label("25:00"), what = DeskUi.heading("Pronto para começar"), chip = new Label();
    final Arc ring = new Arc(0, 0, 118, 118, 90, 0);
    DeskFocus(DeskMain d) {
        this.d = d; tick.setCycleCount(Timeline.INDEFINITE);
        pattern = Math.max(0, Math.min(PATTERNS.length - 1, Integer.parseInt(d.store.get("focus_pattern", "0"))));
        left = total = PATTERNS[pattern][0] * 60; clock.getStyleClass().add("focus-clock"); show();
        chip.getStyleClass().add("focus-chip"); chip.setVisible(false); chip.setManaged(false); chip.setOnMouseClicked(e -> d.open("focus"));
        ring.setType(ArcType.OPEN); ring.getStyleClass().add("focus-ring");
    }

    String phaseName() { return phase == 0 ? "Foco " + round + "/" + ROUNDS : phase == 1 ? "Pausa curta" : "Pausa longa"; }
    static String mmss(int s) { return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60); }
    void show() {
        clock.setText(mmss(left));
        ring.setLength(total == 0 ? 0 : -360.0 * (total - left) / total);
        what.setText(!started ? "Pronto para começar" : (running ? "" : "Em pausa · ") + phaseName() + (subject.isEmpty() || phase != 0 ? "" : " · " + subject));
        chip.setText((phase == 0 ? "Foco " : "Pausa ") + mmss(left)); chip.setVisible(started); chip.setManaged(started);
    }
    void start() { if (!started) { started = true; phase = 0; round = 1; left = total = PATTERNS[pattern][0] * 60; focusStart = System.currentTimeMillis(); } running = true; tick.play(); show(); }
    void pause() { running = false; tick.pause(); show(); }
    void second() { if (!running) return; left--; if (left <= 0) next(true); show(); }
    /** Ends the phase; a finished focus phase is recorded for the subject. */
    void next(boolean finished) {
        if (phase == 0) record(finished ? PATTERNS[pattern][0] : (int) ((System.currentTimeMillis() - focusStart) / 60000));
        if (phase == 0) { phase = round >= ROUNDS ? 2 : 1; }
        else { if (phase == 2) round = 1; else round++; phase = 0; focusStart = System.currentTimeMillis(); }
        left = total = PATTERNS[pattern][phase] * 60;
        if (finished) {
            try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Throwable ignored) {}
            d.status(phase == 0 ? "Pausa terminada: de volta ao foco." : phase == 2 ? "Quatro rondas! Pausa longa de " + PATTERNS[pattern][2] + " minutos." : "Hora da pausa: " + PATTERNS[pattern][1] + " minutos.");
            if (d.stage.isIconified()) d.stage.setIconified(false); d.stage.toFront();
        }
        show();
    }
    void stop() {
        if (started && phase == 0) record((int) ((System.currentTimeMillis() - focusStart) / 60000));
        tick.stop(); running = false; started = false; phase = 0; round = 1; left = total = PATTERNS[pattern][0] * 60; show();
    }
    void record(int minutes) {
        if (minutes < 1) return;
        try { d.store.update("INSERT INTO focus(day,subject,minutes,ended) VALUES(?,?,?,?)", BibleStudyCore.day(System.currentTimeMillis()), subject, minutes, System.currentTimeMillis()); } catch (SQLException ignored) {}
    }
    List<String> subjects() {
        LinkedHashSet<String> out = new LinkedHashSet<>(); for (String s : d.store.get("focus_subjects", "Estudo bíblico,Leitura").split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        try { for (Object[] r : d.store.rows("SELECT DISTINCT subject FROM focus WHERE subject<>'' ORDER BY ended DESC LIMIT 30")) out.add(r[0] + ""); } catch (SQLException ignored) {}
        return new ArrayList<>(out);
    }

    Node view() {
        VBox page = DeskUi.page(DeskUi.title("Foco"));
        // Timer
        Circle track = new Circle(118); track.getStyleClass().add("focus-track");
        StackPane dial = new StackPane(track, ring, new VBox(2, clock, what)); ((VBox) dial.getChildren().get(2)).setAlignment(Pos.CENTER); dial.setMinSize(270, 270);
        ComboBox<String> patterns = new ComboBox<>(); patterns.getItems().addAll(LABELS); patterns.getSelectionModel().select(pattern);
        patterns.setOnAction(e -> { pattern = patterns.getSelectionModel().getSelectedIndex(); d.store.put("focus_pattern", pattern + ""); if (!started) { left = total = PATTERNS[pattern][0] * 60; show(); } });
        ComboBox<String> subjects = new ComboBox<>(); subjects.setEditable(true); subjects.getItems().addAll(subjects()); subjects.setPromptText("Disciplina (opcional)"); subjects.setValue(subject.isEmpty() ? null : subject); subjects.setPrefWidth(230);
        subjects.valueProperty().addListener((o, a, b) -> {
            subject = b == null ? "" : b.trim(); show();
            if (!subject.isEmpty() && !subjects().contains(subject)) { List<String> all = subjects(); all.add(0, subject); d.store.put("focus_subjects", String.join(",", all.subList(0, Math.min(12, all.size())).stream().map(s -> s.replace(",", " ")).toArray(String[]::new))); }
        });
        Button play = DeskUi.button(running ? "Pausar" : started ? "Continuar" : "Começar", true, () -> {});
        play.setOnAction(e -> { if (running) pause(); else start(); play.setText(running ? "Pausar" : "Continuar"); });
        Button skip = DeskUi.button("Saltar fase", false, () -> { if (started) next(false); });
        Button end = DeskUi.button("Terminar", false, () -> { stop(); play.setText("Começar"); d.open("focus"); });
        VBox controls = new VBox(12, DeskUi.muted("Ritmo"), patterns, DeskUi.muted("Disciplina"), subjects, DeskUi.row(play, skip, end));
        HBox timer = new HBox(34, dial, controls); timer.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().add(DeskUi.card(timer));
        // Statistics
        page.getChildren().add(stats());
        show();
        return DeskUi.scroll(page);
    }
    /** Minutes of focus per day this week, and per subject. */
    Node stats() {
        List<String> week = BibleStudyCore.week(System.currentTimeMillis()); Map<String, Integer> perDay = new HashMap<>(); Map<String, Integer> perSubject = new LinkedHashMap<>();
        String today = BibleStudyCore.day(System.currentTimeMillis()); int todayMin = 0, weekMin = 0;
        try {
            for (Object[] r : d.store.rows("SELECT day,subject,SUM(minutes) FROM focus WHERE day>=? GROUP BY day,subject ORDER BY 3 DESC", week.get(0))) {
                String day = r[0] + "", subj = (r[1] + "").isEmpty() ? "Sem disciplina" : r[1] + ""; int m = ((Number) r[2]).intValue();
                perDay.merge(day, m, Integer::sum); if (week.contains(day)) { perSubject.merge(subj, m, Integer::sum); weekMin += m; } if (day.equals(today)) todayMin += m;
            }
        } catch (SQLException ignored) {}
        int max = 30; for (int m : perDay.values()) max = Math.max(max, m);
        HBox bars = new HBox(14); bars.setAlignment(Pos.BOTTOM_LEFT); bars.setMinHeight(150);
        String[] names = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};
        for (int i = 0; i < week.size(); i++) {
            int m = perDay.getOrDefault(week.get(i), 0);
            Region bar = new Region(); bar.getStyleClass().add(week.get(i).equals(today) ? "focus-bar-today" : "focus-bar"); bar.setMinWidth(34); bar.setPrefHeight(Math.max(4, 110.0 * m / max)); bar.setMaxHeight(Region.USE_PREF_SIZE);
            Tooltip.install(bar, new Tooltip(m + " min"));
            VBox col = new VBox(6, DeskUi.muted(m > 0 ? m + "" : ""), bar, DeskUi.muted(i < names.length ? names[i] : week.get(i).substring(8))); col.setAlignment(Pos.BOTTOM_CENTER);
            bars.getChildren().add(col);
        }
        VBox subjects = new VBox(6); for (Map.Entry<String, Integer> e : perSubject.entrySet()) subjects.getChildren().add(DeskUi.row(DeskUi.text(e.getKey()), DeskUi.grow(), DeskUi.muted(hours(e.getValue()))));
        if (perSubject.isEmpty()) subjects.getChildren().add(DeskUi.muted("Ainda sem tempo de foco esta semana."));
        VBox left = new VBox(10, DeskUi.heading("Esta semana"), DeskUi.text("Hoje " + hours(todayMin) + " · semana " + hours(weekMin)), bars);
        VBox right = new VBox(10, DeskUi.heading("Por disciplina"), subjects); HBox.setHgrow(right, Priority.ALWAYS);
        HBox row = new HBox(40, left, right);
        return DeskUi.card(row);
    }
    static String hours(int minutes) { return minutes < 60 ? minutes + " min" : ((minutes / 60) + " h " + (minutes % 60 > 0 ? (minutes % 60) + " min" : "")).trim(); }
}
