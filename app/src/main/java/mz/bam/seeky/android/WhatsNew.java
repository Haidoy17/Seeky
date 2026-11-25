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
        UiPanel panel = new UiPanel(a, "Novidades da 5.53", "Toque numa novidade para a experimentar", "star");
        panel.section("Nesta versão");
        panel.action("speaker", "Narrações em MP3", "No livro: Ouvir › Guardar narração. Escolha MP3 (cerca de 10 vezes mais pequeno do que WAV), um ficheiro por capítulo, com título, livro e número da faixa", false, () -> PlusUi.toast(a, "Abra um livro e escolha Ouvir › Guardar narração."));
        panel.section("Da 5.52");
        panel.action("course", "Cursos, módulos e disciplinas nos dois", "Os cursos e o tipo de cada um (Módulo ou Disciplina) passam também para o Seeky para Windows, com o Seeky ligado. O plano de estudo da Seeky IA pergunta o curso e se cria um módulo ou uma disciplina", false, () -> PlusUi.toast(a, "Abra Estudos › Cursos."));
        panel.section("Da 5.51");
        panel.action("collection", "Perguntar a vários materiais", "Estudos › Estudar com Seeky: escolha até 12 materiais e pergunte. O Seeky envia os trechos mais relevantes de cada um e a resposta diz de que material vem cada ideia", false, () -> a.startActivity(new android.content.Intent(a, StudyAssistantActivity.class).putExtra("many", true)));
        panel.action("book", "Resumir um livro inteiro", "No livro: Ferramentas de leitura › Resumo por etapas › Livro inteiro. No fim, os resumos das partes são juntos num resumo geral guardado nos Estudos", false, () -> PlusUi.toast(a, "Abra o livro e escolha Ferramentas de leitura › Resumo por etapas › Livro inteiro."));
        panel.action("calendar", "Plano de estudo a partir de um tema", "A Seeky IA cria uma disciplina com sessões datadas nas tarefas, cartões de revisão e uma nota com o plano", false, () -> StudyPlanAi.ask(a, sid -> PlusUi.toast(a, "Plano guardado nos Estudos.")));
        panel.section("Da 5.50");
        panel.action("course", "Estudos também no computador", "As disciplinas, os cartões, as perguntas e as tarefas dos Estudos passam a ficar iguais no telemóvel e no Seeky para Windows (Seeky no computador). O que apagar num lado é apagado no outro", false, () -> a.startActivity(new android.content.Intent(a, ToolsActivity.class).putExtra("open_link", true)));
        panel.section("Da 5.49");
        panel.action("phone", "Seeky no computador", "Ferramentas → Seeky no computador: ligue o telemóvel ao Seeky para Windows na mesma rede Wi-Fi (código de 6 números). Notas, onde ficou a leitura, capítulos lidos, versículos para decorar, recordes e músicas favoritas ficam iguais nos dois; também envia e recebe ficheiros. Sincroniza sozinho ao abrir o Seeky", false, () -> a.startActivity(new android.content.Intent(a, ToolsActivity.class).putExtra("open_link", true)));
        panel.section("Da 5.48");
        panel.action("contents", "Comentários bíblicos MyBible", "Ferramentas → Bíblias importadas → Comentários bíblicos: carregue um ficheiro .commentaries. Na leitura, os versículos comentados levam ✎ e o comentário aparece por baixo do capítulo", false, () -> a.startActivity(new android.content.Intent(a, ToolsActivity.class).putExtra("open_bibles", true)));
        panel.action("flash", "Botão Flash no computador", "O Seeky para Windows 5.48 tem o mesmo botão flutuante de pesquisa: Flash, Completa e Pastas, com Tudo, PDF, Documentos, Imagens, Vídeos e Áudio", false, () -> { try { a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Haidoy17/Seeky/tree/main/entrega/windows"))); } catch (Exception ignored) {} });
        panel.section("Da 5.47");
        panel.action("contents", "Títulos de secção nas Bíblias MyBible", "Ao importar um módulo MyBible, os títulos (\"A criação\", \"O Sermão do Monte\"…) aparecem antes dos versículos na leitura. Numa Bíblia já importada: Ferramentas → Bíblias importadas → a Bíblia → Carregar títulos (o módulo com títulos ou o ficheiro .subheadings)", false, () -> a.startActivity(new android.content.Intent(a, ToolsActivity.class).putExtra("open_bibles", true)));
        panel.action("pdf", "Seeky para Windows 5.47", "Música e Vídeos separados, com álbuns, artistas, anos e mais ouvidas como no telemóvel; Livros em estante, grelha ou lista com capas; Bíblia com títulos e comentários MyBible", false, () -> { try { a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Haidoy17/Seeky/tree/main/entrega/windows"))); } catch (Exception ignored) {} });
        panel.section("Da 5.46");
        panel.action("share", "Notas entre o telemóvel e o computador", "Bloco de notas → Pastas → Enviar notas para o computador: um ficheiro .seekynotes com todas as notas, pastas e imagens, que o Seeky para Windows importa. O contrário também: Importar notas do computador. Notas repetidas não são duplicadas e as bloqueadas ficam só no telemóvel", false, () -> a.startActivity(new android.content.Intent(a, NotepadActivity.class)));
        panel.action("pdf", "Seeky para Windows (64 bits)", "O Seeky também existe para o computador, com as mesmas actualizações: Bíblia, estudo, notas com IA, livros com marcações, Foco (Pomodoro), ficheiros e música. Instalador em entrega/windows no GitHub do Seeky", false, () -> { try { a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Haidoy17/Seeky/tree/main/entrega/windows"))); } catch (Exception ignored) {} });
        panel.section("Da 5.45");
        panel.action("book", "Bíblias com versículos agrupados", "Paráfrases como A Mensagem juntam versículos (por exemplo Génesis 1:1-2) e deixam o seguinte vazio: a importação já não pára aí. Se uma importação ficou em Precisa de atenção, abra Tarefas e toque em Retomar pendentes e falhados", false, () -> TaskService.open(a));
        panel.section("Da 5.44");
        panel.action("shield", "Pastas das aplicações (Android/data e Android/obb)", "No Explorar → Pastas das aplicações: com a aplicação gratuita Shizuku, ver, abrir, partilhar, copiar para Transferências, importar, mudar o nome e apagar nas pastas que o Android 11+ esconde. O ecrã explica os 3 passos", false, () -> AppDataActivity.open(a));
        panel.section("Da 5.43");
        panel.action("sparkle", "Gemini 4 Hydrogen", "O modelo mais recente da Google pode ser escolhido em Ligação da IA → Modelo. O Seeky pede-lhe raciocínio rápido (nível baixo); se a sua chave não tiver quota gratuita para ele, usa outro modelo e explica porquê", false, () -> SeekyAi.open(a, 0));
        panel.section("Da 5.42");
        panel.action("sparkle", "IA a funcionar com chaves novas", "O Seeky usa o gemini-3.5-flash-lite (o gemini-2.5-flash-lite já não aceita chaves novas). Em Ligação da IA pode escolher o modelo entre os que a sua chave tem; se a Google retirar um modelo, o Seeky passa sozinho ao seguinte e avisa", false, () -> SeekyAi.open(a, 0));
        panel.section("Da 5.41");
        panel.action("note", "Novos ícones nas notas", "Um conjunto próprio, em dois tons, para a lista, o editor, a barra de ferramentas e o painel Aa (listas, alinhar, recuo, citação, destaque, tabela, gravação, desenho…); nos cartões, ícones mostram tarefas, passagens, imagens, gravações, lembrete e bloqueio", false, () -> NotepadActivity.open(a));
        panel.section("Da 5.40");
        panel.action("sparkle", "Seeky IA nas notas", "Botão ✨ no editor: resumir, corrigir o texto, organizar em tópicos, perguntas de estudo, extrair tarefas, passagens relacionadas, continuar a escrever ou pedir outra coisa — na nota inteira ou só no texto seleccionado", false, () -> NotepadActivity.open(a));
        panel.action("type", "Espaçamento entre linhas", "No painel Aa da nota: Normal, 1,0, 1,15, 1,5 ou 2,0; também no PDF e no Word exportados", false, () -> NotepadActivity.open(a));
        panel.action("edit", "Bloco no painel de selecção", "Seleccione texto no PDF (também página a página), EPUB, Word ou texto e toque em Bloco: a nota do livro abre com as palavras citadas", false, () -> { a.tab = "Livros"; a.render(); });
        panel.section("Da 5.39");
        panel.action("note", "Notas renovadas", "Notas e Tarefas, secções por data (Hoje, 7 dias…), vistas em lista, cartão e grelha, pesquisa em baixo, modelos, fundos de papel (linhas, quadriculado, pontos, cores), bloquear, lembrete e pôr no ecrã inicial", false, () -> NotepadActivity.open(a));
        panel.action("type", "Texto rico nas notas", "Títulos H1–H4, negrito, itálico, sublinhado, riscado, destaque, 6 cores, alinhar, recuo, listas numeradas, tarefas, tabelas, imagens, câmara, desenhos e gravações de voz", false, () -> NotepadActivity.open(a));
        panel.action("word", "Exportar para Word e PDF", "No menu ⋮ da nota: Exportar PDF, Exportar Word (.docx), Markdown ou texto, e Partilhar como PDF ou Word — com títulos, cores, listas, tabelas e imagens", false, () -> NotepadActivity.open(a));
        panel.action("pdfbook", "Notas dentro dos livros", "No leitor, a ferramenta Notas escreve uma nota sem sair do livro (também a partir do texto seleccionado); as notas do livro aparecem ali e no Bloco de notas com o nome do livro", false, () -> { a.tab = "Livros"; a.render(); });
        panel.section("Da 5.38");
        panel.action("star", "Jogo da Bíblia: 60 s e nível Lendário", "60 s em todos os níveis; Médio e Difícil mais profundos (completar passagens, quem disse, ordem dos acontecimentos) e o novo Lendário, com perguntas para meditar e explicação", false, () -> BibleGameActivity.open(a));
        panel.action("note", "Bloco de notas", "Pastas, cores, fixar, tarefas, formatação, modelos (estudo OIA, sermão, devocional), ditar, ouvir, exportar e lixo; as passagens escritas ficam tocáveis e ligam ao estudo bíblico, aos livros e aos Estudos", false, () -> NotepadActivity.open(a));
        panel.section("Da 5.37");
        panel.action("lyrics", "Letras para todos os áudios", "A letra é procurada sozinha ao tocar cada faixa: no telefone, na LRCLIB e no Lyrics.ovh, com o nome limpo (sem \"Official Video\", números de faixa…) e só se o título, o artista e a duração coincidirem", false, () -> LibraryActivity.open(a, MediaLibrary.AUDIO));
        panel.action("mic", "Transcrever com IA", "Pregações, aulas, notas de voz e músicas sem letra na internet: o Gemini escreve o que é dito ou cantado, com os tempos, e a letra aparece aos poucos, sincronizada", false, () -> LibraryActivity.open(a, MediaLibrary.AUDIO));
        panel.action("tasks", "Letras para toda a biblioteca", "Biblioteca de música → Mais → Letras para todas as músicas: procura todas e transcreve as que faltarem (até 25 de cada vez)", false, () -> LibraryActivity.open(a, MediaLibrary.AUDIO));
        panel.section("Da 5.36");
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
