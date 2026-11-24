package mz.bam.seeky.android;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

/** Small building blocks so every Seeky Desktop screen looks the same (styles in seeky.css). */
final class DeskUi {
    private DeskUi() {}
    /** Outline icons on a 24-unit grid. */
    static final java.util.Map<String, String> ICONS = new java.util.HashMap<>();
    static {
        ICONS.put("home", "M3 11 L12 3 L21 11 M5 9 V21 H19 V9");
        ICONS.put("book", "M12 6.5 Q8 4.3 3 5.2 V18.6 Q8 17.7 12 19.8 Q16 17.7 21 18.6 V5.2 Q16 4.3 12 6.5 Z M12 6.5 V19.6");
        ICONS.put("study", "M4 5 H20 V19 H4 Z M8 3 V7 M16 3 V7 M4 10 H20");
        ICONS.put("game", "M12 3 L14.6 8.6 L20.7 9.3 L16.2 13.4 L17.4 19.5 L12 16.5 L6.6 19.5 L7.8 13.4 L3.3 9.3 L9.4 8.6 Z");
        ICONS.put("note", "M12 4.5 H6.5 Q4 4.5 4 7 V17.5 Q4 20 6.5 20 H17 Q19.5 20 19.5 17.5 V12 M18.3 3.2 L20.8 5.7 L12.5 14 L9.3 14.7 L10 11.5 Z");
        ICONS.put("reader", "M5 3 H15 L19 7 V21 H5 Z M15 3 V7 H19 M8 12 H16 M8 16 H14");
        ICONS.put("folder", "M3 7 Q3 5 5 5 H9 L11 7.5 H19 Q21 7.5 21 9.5 V17.5 Q21 19.5 19 19.5 H5 Q3 19.5 3 17.5 Z");
        ICONS.put("music", "M9 18 V5 L20 3 V16 M9 18 A3 3 0 1 1 3 18 A3 3 0 1 1 9 18 Z M20 16 A3 3 0 1 1 14 16 A3 3 0 1 1 20 16 Z");
        ICONS.put("settings", "M12 8.5 A3.5 3.5 0 1 0 12 15.5 A3.5 3.5 0 1 0 12 8.5 Z M12 2.5 V5 M12 19 V21.5 M2.5 12 H5 M19 12 H21.5 M5.3 5.3 L7 7 M17 17 L18.7 18.7 M5.3 18.7 L7 17 M17 7 L18.7 5.3");
        ICONS.put("search", "M10.5 4.2 A6.3 6.3 0 1 0 10.5 16.8 A6.3 6.3 0 1 0 10.5 4.2 Z M15.3 15.3 L20 20");
        ICONS.put("add", "M12 5 V19 M5 12 H19");
        ICONS.put("play", "M7 4 L20 12 L7 20 Z");
        ICONS.put("pause", "M7 4 V20 M17 4 V20");
        ICONS.put("speaker", "M4 9 H8 L13 5 V19 L8 15 H4 Z M16 9 Q18 12 16 15 M18.5 6.5 Q22.5 12 18.5 17.5");
        ICONS.put("sparkle", "M10 3 Q11 11 18 12.5 Q11 14 10 22 Q9 14 2 12.5 Q9 11 10 3 Z M19 2 V7 M16.5 4.5 H21.5");
        ICONS.put("back", "M15 5 L8 12 L15 19");
        ICONS.put("trash", "M4 7 H20 M9 7 V4 H15 V7 M6 7 L7 20 H17 L18 7");
        ICONS.put("export", "M12 3 V15 M7 8 L12 3 L17 8 M5 14 V20 H19 V14");
        ICONS.put("timer", "M12 21 A8 8 0 1 0 12 5 A8 8 0 1 0 12 21 Z M12 9 V13 L14.5 15 M9.5 2.5 H14.5 M18.5 5.5 L20 7");
        ICONS.put("marker", "M6 3 H18 V21 L12 16.5 L6 21 Z");
        ICONS.put("video", "M3 6 H16 V18 H3 Z M16 10 L21 7 V17 L16 14");
        ICONS.put("person", "M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 Z M4.5 20.5 Q5.5 14.5 12 14.5 Q18.5 14.5 19.5 20.5");
        ICONS.put("disc", "M12 21 A9 9 0 1 0 12 3 A9 9 0 1 0 12 21 Z M12 14.5 A2.5 2.5 0 1 0 12 9.5 A2.5 2.5 0 1 0 12 14.5 Z");
        ICONS.put("next", "M5 4 L15 12 L5 20 Z M19 4 V20");
        ICONS.put("prev", "M19 4 L9 12 L19 20 Z M5 4 V20");
        ICONS.put("queue", "M4 6 H20 M4 12 H20 M4 18 H13 M17 15 L21 18 L17 21 Z");
        ICONS.put("lyrics", "M5 4 H19 V16 H10 L6 20 V16 H5 Z M8.5 8.5 H15.5 M8.5 12 H13.5");
        ICONS.put("image", "M4 5 H20 V19 H4 Z M4 16 L9 11 L13 15 L16 12 L20 16 M15.5 8.5 A1.5 1.5 0 1 0 15.5 8.49 Z");
        ICONS.put("power", "M12 3 V12 M7 6.5 A7.5 7.5 0 1 0 17 6.5");
        ICONS.put("phone", "M7 2.5 H17 Q18.5 2.5 18.5 4 V20 Q18.5 21.5 17 21.5 H7 Q5.5 21.5 5.5 20 V4 Q5.5 2.5 7 2.5 Z M10.5 18.5 H13.5");
        ICONS.put("update", "M4 12 A8 8 0 0 1 18 6.5 M20 4 V9 H15 M20 12 A8 8 0 0 1 6 17.5 M4 20 V15 H9");
    }
    static Node icon(String name, double size) {
        SVGPath p = new SVGPath(); p.setContent(ICONS.getOrDefault(name, ICONS.get("note"))); p.getStyleClass().add("icon");
        double s = size / 24.0; p.setScaleX(s); p.setScaleY(s);
        StackPane box = new StackPane(p); box.setMinSize(size, size); box.setPrefSize(size, size); box.setMaxSize(size, size); return box;
    }
    static Label title(String text) { Label l = new Label(text); l.getStyleClass().add("title"); l.setWrapText(true); return l; }
    static Label heading(String text) { Label l = new Label(text); l.getStyleClass().add("heading"); l.setWrapText(true); return l; }
    static Label muted(String text) { Label l = new Label(text); l.getStyleClass().add("muted"); l.setWrapText(true); return l; }
    static Label text(String text) { Label l = new Label(text); l.getStyleClass().add("body"); l.setWrapText(true); return l; }
    static Button button(String text, boolean primary, Runnable action) {
        Button b = new Button(text); b.getStyleClass().add(primary ? "primary" : "secondary"); b.setOnAction(e -> action.run()); return b;
    }
    static Button iconButton(String icon, String tip, Runnable action) {
        Button b = new Button(); b.setGraphic(icon(icon, 20)); b.getStyleClass().add("round"); b.setTooltip(new Tooltip(tip)); b.setAccessibleText(tip); b.setOnAction(e -> action.run()); return b;
    }
    static VBox card(Node... children) { VBox c = new VBox(8, children); c.getStyleClass().add("card"); c.setPadding(new Insets(16)); return c; }
    /** A tile with icon, title and subtitle that opens something. */
    static Node tile(String icon, String title, String subtitle, Runnable action) {
        Node ic = icon(icon, 26); StackPane badge = new StackPane(ic); badge.getStyleClass().add("badge"); badge.setMinSize(44, 44); badge.setMaxSize(44, 44);
        VBox words = new VBox(2, heading(title), muted(subtitle)); HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = new HBox(14, badge, words); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().addAll("card", "tile"); row.setPadding(new Insets(14));
        row.setOnMouseClicked(e -> action.run()); row.setFocusTraversable(true); lift(row);
        row.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) action.run(); });
        return row;
    }
    static ScrollPane scroll(Node content) { ScrollPane s = new ScrollPane(content); s.setFitToWidth(true); s.getStyleClass().add("page-scroll"); smooth(s); return s; }
    /** Wheel scrolling that glides instead of jumping (the same distance, eased over ~200 ms). */
    static void smooth(ScrollPane s) {
        double[] target = {-1};
        javafx.animation.Timeline[] anim = {null};
        s.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0 || e.isControlDown()) return;
            Node c = s.getContent(); if (c == null) return;
            double extra = c.getBoundsInLocal().getHeight() - s.getViewportBounds().getHeight(); if (extra <= 0) return;
            double from = anim[0] != null && anim[0].getStatus() == javafx.animation.Animation.Status.RUNNING && target[0] >= 0 ? target[0] : s.getVvalue();
            double pixels = e.getTextDeltaYUnits() == javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.PAGES ? e.getTextDeltaY() * s.getViewportBounds().getHeight() : e.getDeltaY() * 1.6;
            target[0] = Math.max(0, Math.min(1, from - pixels / extra));
            if (anim[0] != null) anim[0].stop();
            anim[0] = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(220), new javafx.animation.KeyValue(s.vvalueProperty(), target[0], javafx.animation.Interpolator.EASE_OUT)));
            anim[0].play(); e.consume();
        });
    }
    /** A gentle rise on hover for clickable cards. */
    static void lift(Node n) {
        javafx.animation.ScaleTransition up = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(140), n);
        n.setOnMouseEntered(e -> { up.stop(); up.setToX(1.012); up.setToY(1.012); up.playFromStart(); });
        n.setOnMouseExited(e -> { up.stop(); up.setToX(1); up.setToY(1); up.playFromStart(); });
    }
    /** Lays cells in as many columns as fit (1 to 3), recalculated when the window is resized. */
    static void responsive(GridPane grid, java.util.List<Node> cells, double minWidth) {
        int[] cols = {0};
        Runnable layout = () -> {
            double w = grid.getWidth() > 0 ? grid.getWidth() : 900;
            int n = (int) Math.max(1, Math.min(3, Math.floor((w + grid.getHgap()) / (minWidth + grid.getHgap()))));
            if (n == cols[0]) return; cols[0] = n;
            grid.getChildren().clear(); grid.getColumnConstraints().clear();
            for (int i = 0; i < n; i++) { ColumnConstraints c = new ColumnConstraints(); c.setPercentWidth(100.0 / n); c.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(c); }
            for (int i = 0; i < cells.size(); i++) grid.add(cells.get(i), i % n, i / n);
        };
        layout.run();
        grid.widthProperty().addListener((o, a, b) -> layout.run());
    }
    static VBox page(Node... children) { VBox v = new VBox(14, children); v.setPadding(new Insets(24, 28, 28, 28)); v.getStyleClass().add("page"); return v; }
    static HBox row(Node... children) { HBox h = new HBox(10, children); h.setAlignment(Pos.CENTER_LEFT); return h; }
    static Region grow() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
    static void info(String title, String message) { Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK); a.setHeaderText(title); a.setTitle("Seeky"); style(a.getDialogPane()); a.showAndWait(); }
    static boolean confirm(String title, String message) { Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL); a.setHeaderText(title); a.setTitle("Seeky"); style(a.getDialogPane()); return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent(); }
    static String ask(String title, String prompt, String value) { TextInputDialog d = new TextInputDialog(value == null ? "" : value); d.setHeaderText(title); d.setContentText(prompt); d.setTitle("Seeky"); style(d.getDialogPane()); return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).orElse(null); }
    static void style(DialogPane p) { if (DeskMain.instance != null) { p.getStylesheets().setAll(DeskMain.instance.scene.getStylesheets()); p.getStyleClass().addAll(DeskMain.instance.root.getStyleClass()); p.setStyle(DeskMain.instance.root.getStyle()); } }
}
