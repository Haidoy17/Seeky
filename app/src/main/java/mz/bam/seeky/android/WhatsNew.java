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
        UiPanel panel = new UiPanel(a, "Novidades da 5.36", "Toque numa novidade para a experimentar", "star");
        panel.section("Nesta versão");
        panel.action("book", "Estante de madeira", "Os livros ficam de pé em prateleiras, com lombada, folhas, sombras, percentagem e menu ⋮; escolha Nogueira, Carvalho, Cerejeira, Ébano ou Laca branca em Ordenar e filtrar", false, () -> { a.tab = "Livros"; a.render(); });
        panel.action("import", "Importar livros", "Uma pasta inteira (com ou sem subpastas) ou ficheiros escolhidos; formatos; tamanho mínimo; marcar como favorito e pôr numa colecção", false, () -> { a.tab = "Livros"; a.render(); if (a.books != null) a.books.pick(); });
        panel.action("document", "Novos formatos de livro", "FB2, MOBI, AZW3, PRC, ODT, RTF, MHTML e CBZ (banda desenhada) são convertidos para EPUB e abrem com todas as ferramentas do leitor", false, () -> { a.tab = "Livros"; a.render(); });
        panel.section("Da 5.35");
        panel.action("palette", "10 temas novos", "Ardósia, Sálvia, Névoa, Hortelã, Pinheiro, Menta, Petróleo, Quartzo, Folha e Produtivo — das paletas que enviou", false, () -> ThemeSettings.show(a));
        panel.section("Da 5.34");
        panel.note("Painel de selecção junto ao texto (PDF, EPUB, Word e texto): estilos Destacar, Sublinhar, Riscar e Ondulado; seis cores que mudam com o estilo; e Nota, Copiar, Dicionário, Partilhar e Mais. Toque num estilo e depois numa cor — a marcação fica logo no livro. \"Mais\" mostra o menu completo.");
        panel.note("Em ecrã cheio, um rodapé discreto mostra a bateria, a hora, o título e a página (por exemplo 341/383).");
        panel.section("Da 5.33");
        panel.note("Leitor renovado (ao segurar o telefone na vertical): barra de título com a cor da aplicação, régua de páginas numa só linha (‹ régua 341/383 ›), todas as ferramentas visíveis numa grelha — Índice, Pesquisar, Marcadores, Ouvir, Texto, Visualizar, Rodar, Marcações, Nota, Partilhar, Ecrã cheio e Mais — e, no PDF, botões redondos sobre a página para ajustar (página, largura, altura) e mudar entre rolagem vertical e página a página. À volta da página, fundo cinzento nos temas claros.");
        panel.section("Da 5.32");
        panel.action("edit", "As minhas marcações renovadas", "Citações legíveis também no tema escuro, sem falhas entre as palavras; por data ou por livro; filtros por tipo e cor; copiar, partilhar e abrir em cada cartão", false, () -> HighlightsActivity.open(a, null));
        panel.section("Da 5.31");
        panel.action("edit", "Marcar no EPUB e no Word originais", "Na vista original (também \"EPUB ajustado\"), ao seleccionar aparece a barra de cores e Destacar, Cores e mais marcas e Nota no menu; as marcas voltam a aparecer ao reabrir", false, () -> HighlightsActivity.open(a, null));
        panel.section("Da 5.30");
        panel.action("edit", "Barra de marcar ao seleccionar", "No leitor do Seeky, ao seleccionar texto (também no PDF) aparece em baixo uma barra: 6 cores, Sublinhar, Riscar e Nota, com um toque", false, () -> HighlightsActivity.open(a, null));
        panel.note("As marcações só ficam nos livros abertos no Seeky. Se ler noutra aplicação, abra o livro no Seeky (Ficheiros ou Biblioteca, ou \"Abrir com\" → Seeky).");
        panel.section("Da 5.29");
        panel.action("edit", "Marcações com cores", "Seleccione um texto (também no PDF) → Cores e mais marcas: 6 cores, sublinhar, riscar e nota", false, () -> HighlightsActivity.open(a, null));
        panel.action("bookmark", "As minhas marcações", "Todas num só lugar: procurar, ver por completo e abrir o livro na página e no sítio exactos", false, () -> HighlightsActivity.open(a, null));
        panel.action("calendar", "Estudo bíblico", "Leitura de hoje, controlo semanal, planos (Bíblia em 1 ano, NT em 3 meses…), passagens guardadas", false, () -> BibleStudyActivity.open(a));
        panel.action("cards", "Decorar versículos", "Cartões automáticos com livro, capítulo e versículo: escreva ou diga o texto, ou diga o livro", false, () -> BibleStudyActivity.open(a));
        panel.action("star", "Jogo da Bíblia", "10 perguntas, 3 níveis, tempo, ajuda 50/50, respostas por voz e Recorde com o seu nome", false, () -> BibleGameActivity.open(a));
        panel.section("Da 5.28");
        panel.action("book", "Passagens bíblicas ao toque", "Em PDF (também depois do OCR), Word, EPUB e texto: toque em \"Isaías 3.1-7, 12\", \"Jo 3,16\" ou \"Salmo 23\" e a passagem abre ali mesmo", false, () -> BibleLinks.query(a));
        panel.action("copy", "Leitura rápida da passagem", "Copiar, partilhar, ouvir, capítulo inteiro com a passagem destacada, capítulo anterior e seguinte, comparar versões, guardar e tamanho do texto", false, () -> BibleLinks.query(a));
        panel.action("bookmark", "Passagens guardadas", "As passagens que guardar ficam em Consultar passagem → Guardadas", false, () -> BibleLinks.saved(a));
        panel.note("Formatos reconhecidos: 3:16, 3.16 e 3,16; intervalos (1-7), listas (1-7, 12), vários capítulos (3.16-4.2; 5.1), \"e seguintes\" (16ss), capítulos inteiros e livros com um só capítulo (Judas 3). Seleccione um texto e use Abrir passagem bíblica para os casos restantes.");
        panel.section("Da 5.27");
        panel.action("audio", "Leitor de música reorganizado", "Capa · Letra · Fila; controlos na ordem habitual; Equalizador, Velocidade, Temporizador e Partilhar com nome; o resto no menu Mais", false, () -> LibraryActivity.open(a, MediaLibrary.AUDIO));
        panel.action("lyrics", "Letras online", "Procure e escolha a versão certa, ou copie do Google e volte: aplica-se sozinha. Copiar, colar e tamanho do texto", false, () -> LibraryActivity.open(a, MediaLibrary.AUDIO));
        panel.action("screenrotate", "Vídeo: rodar e ajustar", "Rodar o ecrã, rodar a imagem 90°, ajustar, preencher ou esticar; controlos que se escondem", false, () -> LibraryActivity.open(a, MediaLibrary.VIDEO));
        panel.action("chart", "Mais ouvidas", "Hoje, esta semana, este mês ou sempre; adicionadas e ouvidas recentemente", false, () -> LibraryActivity.open(a, MediaLibrary.AUDIO));
        panel.section("Da 5.26");
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
