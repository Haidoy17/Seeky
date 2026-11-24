package mz.bam.seeky.android;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.util.List;

/**
 * Seeky's own window frame on Windows: a title bar in the Seeky colours with minimise, maximise and
 * close, dragging (double-click maximises), resizing from every edge and corner, and maximising to
 * the screen's work area so the taskbar stays visible.
 */
final class DeskWindow {
    static final double EDGE = 6;
    final Stage stage;
    final HBox bar = new HBox();
    final Button max;
    Rectangle2D restore;
    boolean maximized;
    double dragX, dragY;
    Cursor resizing = Cursor.DEFAULT;
    double startX, startY, startW, startH, startSX, startSY;

    DeskWindow(Stage stage) {
        this.stage = stage;
        bar.getStyleClass().add("title-bar"); bar.setAlignment(Pos.CENTER_RIGHT); bar.setMinHeight(34); bar.setPrefHeight(34);
        Region grow = new Region(); HBox.setHgrow(grow, Priority.ALWAYS);
        Button min = control("M5 12 H19", "Minimizar", () -> stage.setIconified(true));
        max = control(MAXIMIZE, "Maximizar", this::toggle);
        Button close = control("M6 6 L18 18 M18 6 L6 18", "Fechar", () -> stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        close.getStyleClass().add("close");
        bar.getChildren().addAll(grow, min, max, close);
        bar.setOnMousePressed(e -> { if (e.getButton() == MouseButton.PRIMARY) { dragX = e.getScreenX() - stage.getX(); dragY = e.getScreenY() - stage.getY(); } });
        bar.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY || resizing != Cursor.DEFAULT) return;
            if (maximized) { // restore under the pointer, keeping the same relative position
                double ratio = dragX / stage.getWidth(); unmaximize();
                dragX = stage.getWidth() * ratio; dragY = Math.min(dragY, 17);
            }
            stage.setX(e.getScreenX() - dragX); stage.setY(e.getScreenY() - dragY);
        });
        bar.setOnMouseReleased(e -> { // dropped at the top of the screen: maximise, like Windows
            if (!maximized && e.getButton() == MouseButton.PRIMARY && e.getScreenY() <= screen().getMinY() + 1 && !e.isStillSincePress()) maximize();
        });
        bar.setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) toggle(); });
    }
    static final String MAXIMIZE = "M6 6 H18 V18 H6 Z", RESTORE = "M8 8 H18 V18 H8 Z M6 15 V6 H15";

    Button control(String path, String tip, Runnable action) {
        SVGPath p = new SVGPath(); p.setContent(path); p.getStyleClass().add("icon"); p.setScaleX(0.5); p.setScaleY(0.5);
        Button b = new Button(); b.setGraphic(new StackPane(p)); b.getStyleClass().add("window-button"); b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(tip)); b.setAccessibleText(tip); b.setOnAction(e -> action.run());
        return b;
    }
    Node bar() { return bar; }

    Rectangle2D screen() {
        List<Screen> s = Screen.getScreensForRectangle(stage.getX() + stage.getWidth() / 2, stage.getY() + 10, 1, 1);
        return (s.isEmpty() ? Screen.getPrimary() : s.get(0)).getVisualBounds();
    }
    void toggle() { if (maximized) unmaximize(); else maximize(); }
    void maximize() {
        if (maximized) return;
        restore = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Rectangle2D v = screen();
        stage.setX(v.getMinX()); stage.setY(v.getMinY()); stage.setWidth(v.getWidth()); stage.setHeight(v.getHeight());
        maximized = true; icon(RESTORE, "Restaurar");
    }
    void unmaximize() {
        if (!maximized) return;
        maximized = false; icon(MAXIMIZE, "Maximizar");
        Rectangle2D r = restore != null ? restore : new Rectangle2D(stage.getX() + 60, stage.getY() + 60, 1240, 800);
        stage.setWidth(r.getWidth()); stage.setHeight(r.getHeight()); stage.setX(r.getMinX()); stage.setY(r.getMinY());
    }
    void icon(String path, String tip) {
        ((SVGPath) ((StackPane) max.getGraphic()).getChildren().get(0)).setContent(path);
        max.getTooltip().setText(tip); max.setAccessibleText(tip);
    }

    /** Resizing from the window's edges (the frame Windows would otherwise draw). */
    void attach(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            Cursor c = maximized ? Cursor.DEFAULT : edge(e.getSceneX(), e.getSceneY(), scene);
            if (c != resizing) { resizing = c; scene.setCursor(c == Cursor.DEFAULT ? null : c); }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (resizing == Cursor.DEFAULT || e.getButton() != MouseButton.PRIMARY) return;
            startX = e.getScreenX(); startY = e.getScreenY(); startW = stage.getWidth(); startH = stage.getHeight(); startSX = stage.getX(); startSY = stage.getY();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (resizing == Cursor.DEFAULT) return;
            double dx = e.getScreenX() - startX, dy = e.getScreenY() - startY, minW = stage.getMinWidth(), minH = stage.getMinHeight();
            Cursor c = resizing;
            if (c == Cursor.E_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.SE_RESIZE) stage.setWidth(Math.max(minW, startW + dx));
            if (c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE) stage.setHeight(Math.max(minH, startH + dy));
            if (c == Cursor.W_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE) { double w = Math.max(minW, startW - dx); stage.setX(startSX + startW - w); stage.setWidth(w); }
            if (c == Cursor.N_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.NE_RESIZE) { double h = Math.max(minH, startH - dy); stage.setY(startSY + startH - h); stage.setHeight(h); }
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> { if (resizing != Cursor.DEFAULT) { resizing = edge(e.getSceneX(), e.getSceneY(), scene); scene.setCursor(resizing == Cursor.DEFAULT ? null : resizing); e.consume(); } });
    }
    static Cursor edge(double x, double y, Scene s) {
        double w = s.getWidth(), h = s.getHeight();
        boolean left = x < EDGE, right = x > w - EDGE, top = y < EDGE, bottom = y > h - EDGE;
        if (top && left) return Cursor.NW_RESIZE; if (top && right) return Cursor.NE_RESIZE;
        if (bottom && left) return Cursor.SW_RESIZE; if (bottom && right) return Cursor.SE_RESIZE;
        if (left) return Cursor.W_RESIZE; if (right) return Cursor.E_RESIZE;
        if (top) return Cursor.N_RESIZE; if (bottom) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }
}
