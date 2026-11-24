package mz.bam.seeky.android;

import javafx.application.Platform;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Seeky em segundo plano: o ícone junto ao relógio do Windows (Abrir Seeky, Pesquisar, Sair) quando a
 * janela fecha mas o botão Flash continua, e uma só instância: abrir o Seeky outra vez mostra a que já
 * está aberta.
 */
final class DeskTray {
    private DeskTray() {}
    static TrayIcon icon;
    static final int PORT = 47913;
    static ServerSocket server;

    static void show(DeskMain d) {
        if (icon != null) return;
        EventQueue.invokeLater(() -> {
            try {
                if (!SystemTray.isSupported()) return;
                Image img = Toolkit.getDefaultToolkit().getImage(DeskMain.class.getResource("/seeky.png"));
                PopupMenu menu = new PopupMenu();
                MenuItem open = new MenuItem("Abrir Seeky"); open.addActionListener(e -> Platform.runLater(d::bringToFront));
                MenuItem search = new MenuItem("Pesquisar (Flash)"); search.addActionListener(e -> Platform.runLater(() -> d.flash.open("")));
                MenuItem quit = new MenuItem("Sair"); quit.addActionListener(e -> Platform.runLater(d::quit));
                menu.add(open); menu.add(search); menu.addSeparator(); menu.add(quit);
                TrayIcon t = new TrayIcon(img, "Seeky", menu); t.setImageAutoSize(true);
                t.addActionListener(e -> Platform.runLater(d::bringToFront));
                SystemTray.getSystemTray().add(t); icon = t;
            } catch (Throwable ignored) {}
        });
    }
    static void tell(String text) { EventQueue.invokeLater(() -> { try { Thread.sleep(800); TrayIcon t = icon; if (t != null) t.displayMessage("Seeky", text, TrayIcon.MessageType.INFO); } catch (Throwable ignored) {} }); }
    static void remove() { TrayIcon t = icon; icon = null; if (t != null) EventQueue.invokeLater(() -> { try { SystemTray.getSystemTray().remove(t); } catch (Throwable ignored) {} }); }

    /** True when another Seeky answered (it was asked to show itself); this one should then stop. */
    static boolean handOver(String what) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 400); s.setSoTimeout(1500);
            Writer w = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8); w.write("SEEKY " + what + "\n"); w.flush();
            String reply = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            return "OK SEEKY".equals(reply);
        } catch (IOException e) { return false; }
    }
    /** Listens (only on this computer) for a second Seeky being opened. */
    static void listen(DeskMain d) {
        try { server = new ServerSocket(PORT, 4, InetAddress.getLoopbackAddress()); } catch (IOException e) { return; }
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(2000);
                    String line = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
                    if (line == null || !line.startsWith("SEEKY ")) continue;
                    Writer w = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8); w.write("OK SEEKY\n"); w.flush();
                    boolean flash = line.contains("--flash");
                    Platform.runLater(() -> { if (flash) d.flash.open(""); else d.bringToFront(); });
                } catch (IOException ignored) {}
            }
        }, "seeky-instancia"); t.setDaemon(true); t.start();
    }
}
