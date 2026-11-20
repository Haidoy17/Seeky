package mz.bam.seeky.android;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

/** Painel "Novidades": aparece uma vez depois de cada actualização e fica em Mais. */
final class WhatsNew {
    static final String KEY = "whats_new_seen";

    private WhatsNew() {}

    /** Mostra as novidades na primeira abertura após uma actualização (nunca numa instalação nova). */
    static void maybeShow(MainActivity a) {
        SharedPreferences p = a.prefs;
        if (MainActivity.VERSION.equals(p.getString(KEY, ""))) return;
        boolean updated = true;
        try {
            PackageInfo info = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            updated = info.lastUpdateTime > info.firstInstallTime;
        } catch (Exception ignored) {}
        p.edit().putString(KEY, MainActivity.VERSION).apply();
        if (updated) a.handler.postDelayed(() -> { if (!a.isFinishing() && !a.dead) show(a); }, 700);
    }

    static void show(MainActivity a) {
        UiPanel panel = new UiPanel(a, "Novidades da 5.26", "Toque numa novidade para a experimentar", "star");
        panel.section("Nesta versão");
        panel.action("audio", "Leitor de música", "Biblioteca por faixas, artistas, álbuns, géneros, anos e pastas; procura automática; fila, aleatório e repetir", false, () -> LibraryActivity.open(a, MediaLibrary.AUDIO));
        panel.action("video", "Leitor de vídeo", "Vídeos por pastas, recentes, anos e favoritos", false, () -> LibraryActivity.open(a, MediaLibrary.VIDEO));
        panel.action("tune", "Equalizador tipo Poweramp", "10 bandas, pré-amplificação, graves, agudos, virtualização, volume extra e balanço", false, () -> EqualizerActivity.open(a));
        panel.note("Letras das músicas no leitor (toque em Aa): sincronizadas, do ficheiro ou da internet, e editáveis.");
        panel.action("refresh", "Aviso de novas versões", "Notificação sempre que sair uma actualização", false, () -> AppUpdates.settings(a));
        panel.section("Da 5.25");
        panel.note("Editor de fotografias: abra uma foto e toque em Editar — recortar com proporções, endireitar, perspectiva, espelhar, 12 filtros, 10 ajustes (brilho, contraste, saturação, calor, realces, sombras, nitidez, vinheta…), desenhar e escrever, desfazer e comparar com o original.");
        panel.note("Partilhar enquanto vê ou ouve: fotos, músicas (também \"o que estou a ouvir\"), vídeos (também o fotograma actual) e documentos (também a página do PDF como imagem e o texto seleccionado).");
        panel.section("Da 5.24");
        panel.action("book", "Bíblias para descarregar", "Mais de 1500 versões em 1200 línguas, incluindo português e línguas de Moçambique", false, () -> BibleCatalog.show(a));
        panel.action("scan", "Digitalizador renovado", "Efeitos, brilho, contraste, nitidez, lupa, várias páginas da câmara ou da galeria, PDF ou JPG e OCR", false, () -> ScanActivity.open(a));
        panel.action("storage", "Mapa do armazenamento renovado", "Resumo do telefone, espaço por tipo, pastas maiores e ficheiros antigos com ícones", false, () -> StorageMap.show(a));
        panel.section("Da 5.23");
        panel.action("refresh", "Actualizações sem erro de assinatura", "A actualização dentro da aplicação já reconhece a assinatura do Seeky em todos os Android", false, () -> AppUpdates.settings(a));
        panel.section("Ficheiros e PDF");
        panel.action("wifi", "Transferir por Wi-Fi", "Enviar e receber ficheiros do computador pelo navegador, sem cabo", false, () -> WifiTransfer.show(a));
        panel.action("pdf", "Ferramentas de PDF", "Juntar, extrair, rodar, comprimir e fotos para PDF", false, () -> PdfTools.show(a));
        panel.action("scan", "Digitalizar documento", "Câmara com recorte automático e filtro de documento", false, () -> ScanActivity.open(a));
        panel.action("storage", "Mapa do armazenamento", "Pastas que ocupam mais espaço e ficheiros grandes antigos", false, () -> StorageMap.show(a));
        panel.action("lock", "Pasta privada", "Esconda ficheiros com a impressão digital ou o PIN", false, () -> PrivateFolder.show(a));
        panel.section("Leitura");
        panel.action("chart", "Estatísticas e metas de leitura", "Tempo por dia, dias seguidos, meta e lembrete", false, () -> ReadingStats.show(a));
        panel.action("translate", "Dicionário e tradução", "Seleccione uma palavra no leitor; guarde-a no Vocabulário", false, () -> WordLookup.vocabulary(a));
        panel.action("globe", "Bibliotecas gratuitas", "Project Gutenberg e catálogos OPDS, directo para a biblioteca", false, () -> OpenLibraries.show(a));
        panel.note("No leitor: Exportar anotações agora também cria PDF e cartões de revisão a partir dos destaques e notas.");
        panel.section("Estudos");
        panel.action("calendar", "Provas e trabalhos", "Contagem decrescente e lembretes", false, () -> StudyAgenda.show(a));
        panel.action("clock", "Pomodoro", "Foco e pausas com o tempo de cada disciplina", false, () -> Pomodoro.show(a));
        panel.section("Ecrã inicial");
        panel.action("widget", "Widget Hoje", "Meta de leitura, Continuar, Ouvir e cartões para rever", false, () -> TodayWidget.pin(a));
        panel.note("Mantenha premido o ícone do Seeky para Pesquisar, Continuar leitura, Limpeza ou Wi-Fi. Novo tema Horário: Areia de dia, Floresta à noite.");
        panel.section("Também no Seeky");
        panel.action("trash", "Limpeza e APKs duplicados", "Cache, miniaturas, APKs repetidos ou já instalados", false, () -> StorageCleaner.show(a));
        panel.action("lock", "Bloqueio da aplicação", "Impressão digital, rosto ou PIN do telefone", false, () -> AppLock.show(a));
        panel.action("restore", "Cópia automática", "Estudos e notas guardados todos os dias ou semanas", false, () -> AutoBackup.show(a));
        panel.action("palette", "Temas com design próprio", "Areia, Floresta, Aurora, Ouro, Néon, Vidro, Gelo, Carvão, Ciano e Horário", false, () -> ThemeSettings.show(a));
        panel.show();
    }
}
