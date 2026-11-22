package mz.bam.seeky.android;

import java.util.*;

/**
 * Bible quiz rules: ten questions per round in three levels. Verse questions use the Bibles the person
 * imported; story, order, group, author and fact questions work without any Bible.
 */
final class BibleGame {
    static final int EASY=0,MEDIUM=1,HARD=2,LEGEND=3,ROUND=10;
    static final String[] LEVELS={"Fácil","Médio","Difícil","Lendário"};
    /** Sixty seconds per question at every level: the difficulty is in the content, not in the clock. */
    static final int[] SECONDS={60,60,60,60};
    static final int[] POINTS={100,150,200,300};

    static final class Verse {final String book,text;final int chapter,verse;Verse(String book,int chapter,int verse,String text){this.book=book;this.chapter=chapter;this.verse=verse;this.text=text;}String reference(){return display(book)+" "+chapter+":"+verse;}}
    /** Verses from an imported Bible; {@link #available()} is false without one. */
    interface Verses {
        boolean available();
        /** Random verses of the given books (any book when null), short enough to read on a card. */
        List<Verse> random(List<String> books,int count,Random random);
        Verse get(String book,int chapter,int verse);
    }
    static final class Question {
        final String kind,prompt,body,answer,reference,explain;final List<String> options;
        Question(String kind,String prompt,String body,List<String> options,String answer,String reference,String explain){this.kind=kind;this.prompt=prompt;this.body=body;this.options=options;this.answer=answer;this.reference=reference;this.explain=explain;}
        /** Written or spoken answer instead of choices. */
        boolean free(){return options.isEmpty();}
        /** Fill-in answer typed or spoken (a word), not a book name. */
        boolean word(){return kind.equals("blankwrite");}
        boolean correct(String given){
            if(given==null)return false;
            if(word()){String a=plain(answer),g=plain(given);return !g.isEmpty()&&(a.equals(g)||g.endsWith(" "+a)||a.length()>=6&&g.equals(a.substring(0,a.length()-1)));}
            if(free()){String book=BibleReferences.bookNamed(given);return !book.isEmpty()&&book.equals(answer);}
            return answer.equals(given);
        }
        /** The answer as shown to the person. */
        String shown(){return free()&&!word()?display(answer):answer;}
    }
    static String plain(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+"," ").replaceAll("\\s+"," ").trim();}

    static final String[][] GROUPS={
        {"Pentateuco","Génesis","Êxodo","Levítico","Números","Deuteronómio"},
        {"Livros históricos","Josué","Juízes","Rute","Primeiro Samuel","Segundo Samuel","Primeiro Reis","Segundo Reis","Primeiro Crónicas","Segundo Crónicas","Esdras","Neemias","Tobias","Judite","Ester","Primeiro Macabeus","Segundo Macabeus"},
        {"Livros poéticos e sapienciais","Jó","Salmos","Provérbios","Eclesiastes","Cântico dos Cânticos","Sabedoria","Eclesiástico"},
        {"Profetas maiores","Isaías","Jeremias","Lamentações","Baruque","Ezequiel","Daniel"},
        {"Profetas menores","Oseias","Joel","Amós","Obadias","Jonas","Miqueias","Naum","Habacuque","Sofonias","Ageu","Zacarias","Malaquias"},
        {"Evangelhos","Mateus","Marcos","Lucas","João"},
        {"História da Igreja","Actos"},
        {"Cartas de Paulo","Romanos","Primeira Coríntios","Segunda Coríntios","Gálatas","Efésios","Filipenses","Colossenses","Primeira Tessalonicenses","Segunda Tessalonicenses","Primeira Timóteo","Segunda Timóteo","Tito","Filémon"},
        {"Cartas gerais","Hebreus","Tiago","Primeira Pedro","Segunda Pedro","Primeira João","Segunda João","Terceira João","Judas"},
        {"Profecia","Apocalipse"}};
    static final String[] POPULAR={"Génesis","Êxodo","Josué","Juízes","Rute","Primeiro Samuel","Segundo Samuel","Primeiro Reis","Jó","Salmos","Provérbios","Eclesiastes","Isaías","Jeremias","Daniel","Jonas","Mateus","Marcos","Lucas","João","Actos","Romanos","Primeira Coríntios","Gálatas","Efésios","Filipenses","Hebreus","Tiago","Primeira Pedro","Primeira João","Apocalipse"};
    static final String[] FAMOUS={"Génesis 1:1","Génesis 1:27","Êxodo 20:3","Deuteronómio 6:5","Josué 1:9","Primeiro Samuel 16:7","Salmos 23:1","Salmos 46:1","Salmos 91:1","Salmos 119:105","Salmos 121:1","Provérbios 3:5","Provérbios 22:6","Eclesiastes 3:1","Isaías 9:6","Isaías 40:31","Isaías 53:5","Jeremias 29:11","Lamentações 3:22","Miqueias 6:8","Malaquias 3:10","Mateus 5:9","Mateus 5:14","Mateus 6:33","Mateus 7:7","Mateus 11:28","Mateus 28:19","Marcos 16:15","Lucas 2:11","João 1:1","João 3:16","João 8:32","João 11:25","João 13:34","João 14:6","João 15:5","Actos 1:8","Romanos 3:23","Romanos 6:23","Romanos 8:28","Romanos 12:2","Primeira Coríntios 13:4","Segunda Coríntios 5:17","Gálatas 5:22","Efésios 2:8","Efésios 6:11","Filipenses 4:6","Filipenses 4:13","Colossenses 3:23","Segunda Timóteo 3:16","Hebreus 11:1","Hebreus 13:8","Tiago 1:5","Primeira Pedro 5:7","Primeira João 4:8","Apocalipse 3:20","Apocalipse 21:4"};
    /** {level, book, story, books that must not be offered as wrong answers}. */
    static final String[][] STORIES={
        {"0","Génesis","A criação do mundo, do primeiro homem e da primeira mulher",""},
        {"0","Génesis","A arca de Noé e o dilúvio",""},
        {"0","Génesis","A torre de Babel e a confusão das línguas",""},
        {"0","Génesis","Caim e Abel, os primeiros irmãos",""},
        {"0","Êxodo","Moisés e as pragas do Egipto",""},
        {"0","Êxodo","A travessia do mar Vermelho",""},
        {"0","Êxodo","Moisés recebe os Dez Mandamentos no monte Sinai","Deuteronómio"},
        {"0","Josué","A queda das muralhas de Jericó",""},
        {"0","Juízes","Sansão e Dalila",""},
        {"0","Rute","Rute fica com Noemi: «o teu povo será o meu povo»",""},
        {"0","Primeiro Samuel","O jovem David vence o gigante Golias",""},
        {"0","Salmos","«O Senhor é o meu pastor, nada me faltará»",""},
        {"0","Daniel","Daniel na cova dos leões",""},
        {"0","Jonas","Jonas é engolido por um grande peixe",""},
        {"0","Ester","A rainha Ester salva o seu povo",""},
        {"0","Jó","Um homem justo perde tudo e mantém a fé",""},
        {"0","Lucas","O nascimento de Jesus e os pastores de Belém","Mateus"},
        {"0","Mateus","Os magos do Oriente seguem a estrela",""},
        {"0","Mateus","O Sermão do Monte e as bem-aventuranças","Lucas"},
        {"0","João","A água transformada em vinho nas bodas de Caná",""},
        {"0","João","A ressurreição de Lázaro",""},
        {"0","Lucas","A parábola do filho pródigo",""},
        {"0","Lucas","A parábola do bom samaritano",""},
        {"0","Actos","O Espírito Santo desce no dia de Pentecostes",""},
        {"0","Apocalipse","Um novo céu, uma nova terra e a nova Jerusalém",""},
        {"1","Génesis","José é vendido pelos irmãos e chega a governador do Egipto",""},
        {"1","Êxodo","Deus fala a Moisés numa sarça que arde sem se consumir",""},
        {"1","Juízes","Gideão vence com apenas trezentos homens",""},
        {"1","Primeiro Samuel","O menino Samuel ouve Deus chamá-lo durante a noite",""},
        {"1","Primeiro Reis","Salomão julga o caso das duas mães e do bebé",""},
        {"1","Primeiro Reis","Elias desafia os profetas de Baal no monte Carmelo",""},
        {"1","Segundo Reis","Elias é levado ao céu num carro de fogo",""},
        {"1","Neemias","A reconstrução das muralhas de Jerusalém","Esdras"},
        {"1","Daniel","Três jovens saem ilesos de uma fornalha ardente",""},
        {"1","Lucas","Zaqueu sobe a uma árvore para ver Jesus",""},
        {"1","João","Jesus conversa com uma mulher samaritana junto ao poço",""},
        {"1","João","Nicodemos visita Jesus de noite: é preciso nascer de novo",""},
        {"1","Actos","A conversão de Saulo no caminho de Damasco",""},
        {"1","Primeira Coríntios","O grande capítulo sobre o amor",""},
        {"1","Efésios","A armadura de Deus",""},
        {"1","Hebreus","A galeria dos heróis da fé",""},
        {"1","Gálatas","O fruto do Espírito: amor, alegria, paz…",""},
        {"1","Eclesiastes","«Tudo tem o seu tempo determinado»",""},
        {"1","Provérbios","O retrato da mulher virtuosa",""},
        {"2","Segundo Reis","Naamã é curado da lepra ao mergulhar sete vezes no Jordão",""},
        {"2","Daniel","Uma mão escreve na parede durante o banquete de Belsazar",""},
        {"2","Ezequiel","Um vale de ossos secos volta à vida",""},
        {"2","Isaías","O servo sofredor, ferido pelas nossas transgressões",""},
        {"2","Actos","Paulo naufraga e chega à ilha de Malta",""},
        {"2","Actos","Pedro é libertado da prisão por um anjo",""},
        {"2","Filémon","Paulo intercede pelo escravo fugitivo Onésimo",""},
        {"2","Números","A jumenta de Balaão fala",""},
        {"2","Juízes","Débora, juíza e profetisa, conduz Israel à vitória",""},
        {"2","Primeiro Samuel","Ana ora pelo filho no santuário de Siló",""},
        {"2","Segundo Samuel","O profeta Natã conta a David a parábola da ovelha",""},
        {"2","Esdras","O regresso do exílio e a reconstrução do templo","Neemias,Ageu"},
        {"2","Oseias","Um profeta casa com uma mulher infiel como sinal do amor de Deus",""},
        {"2","Joel","«Derramarei o meu Espírito sobre toda a carne»","Actos"},
        {"2","Amós","Um pastor de Tecoa denuncia a injustiça social",""},
        {"2","Miqueias","A profecia de que o Messias nasceria em Belém","Mateus"},
        {"2","Malaquias","«Trazei todos os dízimos à casa do tesouro»",""},
        {"2","Tiago","«A fé sem obras é morta»",""},
        {"2","Judas","O arcanjo Miguel disputa com o diabo o corpo de Moisés",""},
        {"2","Génesis","Jacob luta durante a noite e passa a chamar-se Israel",""},
        {"2","Josué","O sol detém-se sobre Gibeão",""},
        {"2","Ageu","Um apelo para reconstruir a casa do Senhor","Esdras,Neemias"},
        {"2","Zacarias","A visão do candelabro de ouro e das duas oliveiras",""}};
    /** {level, question, answer, wrong answers…}. */
    static final String[][] FACTS={
        {"0","Qual é o primeiro livro da Bíblia?","Génesis","Êxodo","Mateus","Salmos"},
        {"0","Qual é o último livro da Bíblia?","Apocalipse","Malaquias","Judas","Actos"},
        {"0","Quantos evangelhos há na Bíblia?","4","3","5","12"},
        {"0","Qual é o primeiro livro do Novo Testamento?","Mateus","Marcos","João","Actos"},
        {"2","Quantos livros tem o Novo Testamento?","27","24","29","39"},
        {"2","Quantos livros tem o Antigo Testamento na Bíblia protestante?","39","27","46","66"},
        {"2","Quantos livros tem a Bíblia protestante?","66","73","62","77"},
        {"2","Qual é o livro com mais capítulos?","Salmos","Isaías","Génesis","Jeremias"},
        {"2","Qual é o capítulo mais longo da Bíblia?","Salmo 119","Salmo 23","Isaías 53","Salmo 150"},
        {"2","Qual é o capítulo mais curto da Bíblia?","Salmo 117","Salmo 23","Salmo 1","Salmo 150"},
        {"2","Qual destes livros tem um só capítulo?","Judas","Rute","Tito","Jonas"},
        {"2","Em que língua foi escrita a maior parte do Antigo Testamento?","Hebraico","Grego","Latim","Árabe"},
        {"2","Em que língua foi escrito o Novo Testamento?","Grego","Hebraico","Latim","Árabe"},
        {"2","Qual é o último livro do Antigo Testamento na ordem protestante?","Malaquias","Zacarias","Daniel","Ageu"},
        {"2","Quantas cartas de Paulo há no Novo Testamento, sem contar Hebreus?","13","7","10","14"}};
    /** {author, books…} as tradition attributes them. */
    static final String[][] AUTHORS={
        {"Moisés","Génesis","Êxodo","Levítico","Números","Deuteronómio"},
        {"David","Salmos"},
        {"Salomão","Provérbios","Eclesiastes","Cântico dos Cânticos"},
        {"Jeremias","Lamentações"},
        {"Lucas","Actos"},
        {"João","Apocalipse","Primeira João","Segunda João","Terceira João"},
        {"Paulo","Romanos","Primeira Coríntios","Segunda Coríntios","Gálatas","Efésios","Filipenses","Colossenses","Primeira Tessalonicenses","Segunda Tessalonicenses","Primeira Timóteo","Segunda Timóteo","Tito","Filémon"},
        {"Pedro","Primeira Pedro","Segunda Pedro"}};
    /** {level, saying, who said it, wrong…, reference}. */
    static final String[][] QUOTES={
        {"2","«Eis aqui a serva do Senhor; cumpra-se em mim segundo a tua palavra»","Maria","Isabel","Ana","Marta","Lucas 1:38"},
        {"2","«Sou eu guardador do meu irmão?»","Caim","Abel","Esaú","Lameque","Génesis 4:9"},
        {"2","«Ainda que ele me mate, nele esperarei»","Jó","David","Jeremias","Elias","Jó 13:15"},
        {"2","«Eu e a minha casa serviremos ao Senhor»","Josué","Moisés","Calebe","Samuel","Josué 24:15"},
        {"2","«Fala, porque o teu servo ouve»","Samuel","Eli","Isaías","Jonas","1 Samuel 3:10"},
        {"2","«Eis-me aqui, envia-me a mim»","Isaías","Jeremias","Ezequiel","Moisés","Isaías 6:8"},
        {"2","«Se perecer, pereci»","Ester","Rute","Débora","Mardoqueu","Ester 4:16"},
        {"2","«Senhor, para quem iremos nós? Tu tens as palavras da vida eterna»","Pedro","João","Tomé","André","João 6:68"},
        {"2","«Senhor meu, e Deus meu!»","Tomé","Pedro","Filipe","Natanael","João 20:28"},
        {"2","«Que é a verdade?»","Pilatos","Herodes","Caifás","Félix","João 18:38"},
        {"2","«Para mim o viver é Cristo, e o morrer é ganho»","Paulo","Pedro","Timóteo","Barnabé","Filipenses 1:21"},
        {"2","«É necessário que ele cresça e que eu diminua»","João Baptista","Pedro","Paulo","André","João 3:30"},
        {"3","«Vós bem intentastes mal contra mim, porém Deus o tornou em bem»","José","Jacob","Judá","Moisés","Génesis 50:20"},
        {"3","«Ainda que a figueira não floresça… todavia eu me alegrarei no Senhor»","Habacuque","Jeremias","Naum","Joel","Habacuque 3:17-18"},
        {"3","«O Senhor o deu, e o Senhor o tomou: bendito seja o nome do Senhor»","Jó","David","Salomão","Elias","Jó 1:21"},
        {"3","«Eu creio, Senhor! Ajuda a minha incredulidade»","O pai do menino possesso","Jairo","O centurião","Nicodemos","Marcos 9:24"},
        {"3","«Senhor, não sou digno de que entres debaixo do meu telhado»","O centurião","Zaqueu","Jairo","Nicodemos","Mateus 8:8"},
        {"3","«Ainda que todos se escandalizem em ti, eu nunca me escandalizarei»","Pedro","João","Tiago","Judas","Mateus 26:33"},
        {"3","«Por pouco me queres persuadir a que me faça cristão»","Agripa","Festo","Félix","Gálio","Actos 26:28"},
        {"3","«O meu Deus enviou o seu anjo e fechou a boca dos leões»","Daniel","Sadraque","Ezequiel","Neemias","Daniel 6:22"},
        {"3","«Senhor, lembra-te de mim, quando entrares no teu reino»","O ladrão arrependido","O centurião","Barrabás","José de Arimateia","Lucas 23:42"},
        {"3","«Aonde quer que tu fores, irei eu»","Rute","Orfa","Noemi","Abigail","Rute 1:16"}};
    /** Events in biblical order. */
    static final String[] EVENTS={"A criação do mundo","O dilúvio","A torre de Babel","A chamada de Abraão","Jacob sonha com a escada até ao céu","José governa o Egipto","Moisés diante da sarça ardente","A travessia do mar Vermelho","Os Dez Mandamentos no Sinai","A queda de Jericó","Gideão vence com trezentos homens","Samuel unge David","Salomão constrói o templo","O reino divide-se entre Roboão e Jeroboão","Elias no monte Carmelo","A queda de Samaria","Nabucodonosor destrói Jerusalém","O decreto de Ciro e o regresso do exílio","Neemias reconstrói as muralhas","O nascimento de Jesus","O baptismo de Jesus no Jordão","A última ceia","A ressurreição de Jesus","O Pentecostes","A conversão de Saulo","O concílio de Jerusalém","Paulo naufraga em Malta"};
    /** {level, text with ____, missing word, wrong words…, reference}. */
    static final String[][] BLANKS={
        {"2","No princípio era o ____, e o Verbo estava com Deus, e o Verbo era Deus.","Verbo","Espírito","Filho","Senhor","João 1:1"},
        {"2","Porque Deus amou o mundo de tal maneira que deu o seu Filho ____.","unigénito","amado","primogénito","eterno","João 3:16"},
        {"2","O Senhor é o meu ____; nada me faltará.","pastor","rei","refúgio","escudo","Salmos 23:1"},
        {"2","Lâmpada para os meus pés é a tua ____, e luz para o meu caminho.","palavra","lei","verdade","graça","Salmos 119:105"},
        {"2","Confia no Senhor de todo o teu ____ e não te estribes no teu próprio entendimento.","coração","espírito","ser","poder","Provérbios 3:5"},
        {"2","Porque pela ____ sois salvos, por meio da fé.","graça","lei","obra","verdade","Efésios 2:8"},
        {"2","O salário do pecado é a morte, mas o ____ gratuito de Deus é a vida eterna.","dom","favor","perdão","amor","Romanos 6:23"},
        {"2","Posso todas as coisas naquele que me ____.","fortalece","sustenta","guarda","chama","Filipenses 4:13"},
        {"2","Buscai primeiro o reino de Deus, e a sua ____.","justiça","glória","paz","verdade","Mateus 6:33"},
        {"2","Eu sou o caminho, e a ____, e a vida.","verdade","luz","porta","paz","João 14:6"},
        {"2","Vinde a mim, todos os que estais cansados e ____, e eu vos aliviarei.","oprimidos","tristes","feridos","perdidos","Mateus 11:28"},
        {"2","Bem-aventurados os limpos de ____, porque eles verão a Deus.","coração","mãos","alma","olhos","Mateus 5:8"},
        {"2","Eu sou a ____ e a vida; quem crê em mim, ainda que esteja morto, viverá.","ressurreição","luz","verdade","porta","João 11:25"},
        {"3","Os que esperam no Senhor renovarão as suas ____; subirão com asas como águias.","forças","vestes","almas","promessas","Isaías 40:31"},
        {"3","Mas ele foi ferido pelas nossas ____, e moído pelas nossas iniquidades.","transgressões","dores","culpas","fraquezas","Isaías 53:5"},
        {"3","Ainda que eu andasse pelo vale da sombra da ____, não temeria mal algum.","morte","noite","dor","guerra","Salmos 23:4"},
        {"3","Aquietai-vos e sabei que eu sou ____.","Deus","rei","santo","fiel","Salmos 46:10"},
        {"3","Cria em mim, ó Deus, um coração ____, e renova em mim um espírito recto.","puro","novo","manso","forte","Salmos 51:10"},
        {"3","A resposta branda desvia o ____, mas a palavra dura suscita a ira.","furor","mal","ódio","medo","Provérbios 15:1"},
        {"3","Deleita-te também no Senhor, e ele te concederá os ____ do teu coração.","desejos","sonhos","planos","segredos","Salmos 37:4"},
        {"3","Ora, a fé é o firme fundamento das coisas que se ____, e a prova das coisas que se não vêem.","esperam","pedem","crêem","sabem","Hebreus 11:1"},
        {"3","Ensina a criança no ____ em que deve andar; e até quando envelhecer não se desviará dele.","caminho","temor","tempo","amor","Provérbios 22:6"},
        {"3","Tudo tem o seu tempo determinado, e há tempo para todo o ____ debaixo do céu.","propósito","trabalho","desejo","homem","Eclesiastes 3:1"}};
    /** {level, question, answer, wrong…, explanation}: knowledge and reasoning (Difícil) or deep reflection (Lendário). */
    static final String[][] DEEP={
        {"2","Porque é que Moisés não entrou na terra prometida?","Feriu a rocha em vez de lhe falar","Fez o bezerro de ouro","Fugiu do Egipto","Casou com uma estrangeira","Números 20:8-12: Deus mandou falar à rocha; Moisés feriu-a duas vezes e não santificou o Senhor diante do povo."},
        {"2","Na parábola do semeador, o que representa a semente?","A palavra de Deus","A fé","Os discípulos","O reino","Lucas 8:11: «A semente é a palavra de Deus»."},
        {"2","Segundo Hebreus, Jesus é sacerdote segundo a ordem de quem?","Melquisedeque","Arão","Levi","Zadoque","Hebreus 5:6, citando o Salmo 110:4."},
        {"2","Quem foi o primeiro mártir da Igreja?","Estêvão","Tiago","Pedro","Filipe","Actos 7: Estêvão é apedrejado enquanto Saulo guarda as vestes."},
        {"2","Qual era a profissão de Lucas?","Médico","Pescador","Cobrador de impostos","Fabricante de tendas","Colossenses 4:14: «Lucas, o médico amado»."},
        {"2","Além de pregar, Paulo trabalhava como…","Fabricante de tendas","Pescador","Médico","Carpinteiro","Actos 18:3: trabalhava com Áquila e Priscila."},
        {"2","Que rei viu uma mão escrever na parede?","Belsazar","Nabucodonosor","Dario","Ciro","Daniel 5: «Mene, mene, tequel e parsim»."},
        {"2","O que pediu Salomão a Deus em Gibeão?","Sabedoria para governar o povo","Riquezas","Vida longa","Vitória sobre os inimigos","1 Reis 3:9: um coração entendido para julgar o povo."},
        {"2","Por quantas moedas de prata Judas entregou Jesus?","30","20","40","50","Mateus 26:15. José tinha sido vendido por 20 (Génesis 37:28)."},
        {"2","Por quantas moedas de prata José foi vendido pelos irmãos?","20","30","40","10","Génesis 37:28."},
        {"2","Que discípulo era chamado Dídimo (gémeo)?","Tomé","Tiago","Mateus","Bartolomeu","João 11:16."},
        {"2","Que outro nome tinha o apóstolo Mateus?","Levi","Simão","Tadeu","Natanael","Marcos 2:14 e Lucas 5:27."},
        {"2","Qual destes NÃO aparece no fruto do Espírito em Gálatas 5:22-23?","Sabedoria","Amor","Domínio próprio","Mansidão","O fruto: amor, alegria, paz, longanimidade, benignidade, bondade, fé, mansidão, temperança."},
        {"2","Que juiz fez um voto precipitado que lhe custou a filha?","Jefté","Gideão","Sansão","Eúde","Juízes 11:30-39."},
        {"2","Qual foi a primeira praga do Egipto?","As águas tornaram-se em sangue","Rãs","Piolhos","Trevas","Êxodo 7:20."},
        {"2","Que mulher foi juíza em Israel?","Débora","Jael","Ana","Miriã","Juízes 4:4."},
        {"2","Como se chamava o servo a quem Pedro cortou a orelha?","Malco","Caifás","Anás","Barrabás","João 18:10."},
        {"2","Quem ajudou Jesus a levar a cruz?","Simão de Cirene","José de Arimateia","Nicodemos","Lázaro","Marcos 15:21."},
        {"2","Que rei de Judá mandou ler o livro da Lei encontrado no templo e fez uma reforma?","Josias","Ezequias","Asa","Josafá","2 Reis 22–23."},
        {"2","Quantos anos durou o cativeiro na Babilónia, segundo Jeremias?","70","40","400","7","Jeremias 25:11 e 29:10."},
        {"3","Hebreus 10:5-7 («corpo me preparaste») aplica a Cristo um texto de que livro?","Salmos","Isaías","Levítico","Jeremias","Hebreus cita o Salmo 40:6-8: a obediência do Filho substitui os sacrifícios."},
        {"3","Em Romanos 4, a fé de Abraão mostra que a justiça vem…","Pela fé, antes da circuncisão e da Lei","Pela circuncisão, sinal da aliança","Pela obediência ao sacrificar Isaque","Pela descendência física de Abraão","Romanos 4:9-16: Abraão foi justificado antes de ser circuncidado, para ser pai de todos os que crêem."},
        {"3","Tiago 2:21 diz que Abraão foi justificado pelas obras. Como se harmoniza com Romanos 4?","As obras mostram a fé viva que já justificava","Falam de dois Abraões diferentes","Paulo e Tiago contradizem-se","A justificação só vem depois das obras","Tiago 2:18-22: a fé cooperou com as obras e foi aperfeiçoada por elas; Paulo fala da raiz, Tiago do fruto."},
        {"3","Que verdade liga a serpente de bronze (Números 21) a João 3:14-15?","Olhar com fé para o que foi levantado dá vida","A serpente representa a vitória de Moisés","Israel devia adorar a serpente","Jesus seria picado como o povo","Jesus aplica a figura a si: quem nele crê tem a vida eterna."},
        {"3","Mateus 2:15 («do Egipto chamei o meu filho») cita Oseias 11:1. De quem falava Oseias originalmente?","De Israel a sair do Egipto","De Jesus menino","De José do Egipto","De Moisés","Mateus mostra Jesus a reviver a história de Israel, agora com fidelidade perfeita."},
        {"3","«Deus proverá para si o cordeiro» (Génesis 22:8) antecipa que verdade?","Cristo, o Cordeiro que Deus dá","O fim do templo","A posse da terra","A circuncisão","João 1:29: «Eis o Cordeiro de Deus, que tira o pecado do mundo»."},
        {"3","Porque diz Jesus, em Mateus 5:17, que não veio revogar a Lei?","Veio cumpri-la plenamente","A Lei já estava abolida","Só os fariseus a deviam cumprir","Veio juntar leis cerimoniais","Cristo cumpre a Lei e os Profetas, levando-os ao seu sentido pleno."},
        {"3","Em Lucas 15, quem retrata mais directamente os fariseus que murmuravam (15:2)?","O filho mais velho","O filho pródigo","O pai","Os servos","O irmão mais velho recusa a festa da graça, como os que murmuravam porque Jesus recebia pecadores."},
        {"3","O que revela o véu do templo rasgado de alto a baixo (Mateus 27:51)?","O caminho até Deus aberto por Cristo","A destruição de Jerusalém nesse dia","A ira dos sacerdotes","Só um tremor de terra","Hebreus 10:19-20: entramos pelo novo e vivo caminho, pelo véu, que é a sua carne."},
        {"3","Porque chama Paulo à Lei um «aio» até Cristo (Gálatas 3:24)?","Conduz a Cristo, mostrando a nossa necessidade","Era um castigo para Israel","Substitui a fé","Era só para os gentios","O aio guiava a criança até ao mestre: a Lei leva-nos a Cristo, para sermos justificados pela fé."},
        {"3","Que paradoxo Paulo aprende em 2 Coríntios 12:9-10?","O poder de Deus aperfeiçoa-se na fraqueza","A força vem do esforço humano","Quem sofre está longe de Deus","A graça só chega aos fortes","«A minha graça te basta» — quando estou fraco, então sou forte."},
        {"3","Porque pergunta Jesus três vezes a Pedro «amas-me?» (João 21)?","Para o restaurar depois das três negações","Pedro não ouvira à primeira","Era um costume judaico","Para escolher outro apóstolo","Três negações, três confissões de amor e a missão: «apascenta as minhas ovelhas»."},
        {"3","Em que livro Filipe explica Isaías 53 a um eunuco etíope?","Actos","Hebreus","Romanos","Lucas","Actos 8:32-35: começando nesta Escritura, anunciou-lhe Jesus."},
        {"3","Génesis 50:20 vê Deus a tornar o mal em bem. Que texto do Novo Testamento diz o mesmo?","Romanos 8:28","João 3:16","Mateus 5:9","Tiago 1:27","«Todas as coisas contribuem juntamente para o bem daqueles que amam a Deus»."},
        {"3","O que ensina o livro de Jó sobre o sofrimento?","Nem todo o sofrimento é castigo de um pecado concreto","Quem sofre pecou sempre","Deus não vê o sofrimento","O sofrimento destrói a fé","Os amigos de Jó erraram ao culpá-lo; Deus responde com a sua grandeza (Jó 38–42)."},
        {"3","Depois de «vaidade de vaidades», a que conclusão chega Eclesiastes (12:13)?","Temer a Deus e guardar os seus mandamentos","Aproveitar a vida porque nada importa","Juntar riquezas","Fugir do mundo","«Este é o dever de todo o homem.»"},
        {"3","Porque é Habacuque 2:4 («o justo viverá pela fé») citado três vezes no Novo Testamento?","É base da justificação pela fé","Fala só do regresso do exílio","Promete riqueza aos justos","Ensina a salvação pelas obras","Romanos 1:17, Gálatas 3:11 e Hebreus 10:38."},
        {"3","Que liga a Páscoa de Êxodo 12 a 1 Coríntios 5:7?","Cristo é o nosso cordeiro pascal","A Páscoa deixou de ter sentido","Paulo manda celebrar a Páscoa judaica","O fermento representa o Espírito","Como o sangue do cordeiro livrou Israel, o sacrifício de Cristo liberta-nos."},
        {"3","O que distingue a nova aliança de Jeremias 31:31-34?","A lei escrita no coração","Novos sacrifícios de animais","Um novo templo de pedra","Uma terra só para Israel","Hebreus 8 aplica-a a Cristo: perdão completo e conhecimento de Deus."},
        {"3","Que título de Daniel 7:13 Jesus aplica a si próprio?","Filho do Homem","Filho de David","Leão de Judá","Príncipe da Paz","Marcos 14:62: «vereis o Filho do Homem… vindo com as nuvens do céu»."},
        {"3","Porque resistiu Paulo a Pedro em Antioquia (Gálatas 2:11-14)?","Pedro afastou-se dos gentios por medo","Pedro negou Jesus","Pedro escreveu outro evangelho","Pedro recusou baptizar","A comunhão à mesa mostrava a verdade do evangelho: judeus e gentios justificados pela fé."},
        {"3","Em Filipenses 2:6-8, o que fez Cristo «sendo em forma de Deus»?","Esvaziou-se e humilhou-se até à cruz","Deixou de ser Deus","Exigiu ser servido","Fugiu do sofrimento","O caminho da humildade é o modelo para a Igreja: «haja em vós o mesmo sentimento»."},
        {"3","Onde está o primeiro anúncio do evangelho (o «protoevangelho»)?","Génesis 3:15","Génesis 12:3","Isaías 7:14","Salmo 22","A descendência da mulher ferirá a cabeça da serpente."},
        {"3","Marcos 10:45 («dar a sua vida em resgate de muitos») ecoa que figura de Isaías?","O Servo do Senhor","O rebento de Jessé","Emanuel","O vigia","Isaías 52:13–53:12: o Servo que leva o pecado de muitos."},
        {"3","Que pecado de David levou Natã a contar a parábola da ovelha?","O adultério com Bate-Seba e a morte de Urias","O censo do povo","Poupar Saul","Dançar diante da arca","2 Samuel 11–12: «Tu és este homem»."},
        {"3","Segundo Hebreus 11:19, em que confiava Abraão ao oferecer Isaque?","Que Deus o podia ressuscitar","Que o sacrifício não aconteceria","Que teria outro filho","Que Isaque era culpado","A promessa vinha por Isaque; Abraão confiou no Deus que dá vida aos mortos."},
        {"3","Que conflito descreve Paulo em Romanos 7?","Querer o bem e fazer o mal que não quer","Entre judeus e gentios","Entre Pedro e Tiago","Entre Roma e Jerusalém","A resposta vem em Romanos 7:25–8:2: graças a Deus por Jesus Cristo."}};
    static final String[] PEOPLE={"Moisés","David","Salomão","Paulo","João","Lucas","Pedro","Jeremias","Isaías","Mateus","Tiago","Samuel","Esdras"};

    /** "Primeira Coríntios" → "1 Coríntios". */
    static String display(String book){return book.replaceFirst("^Primeir[oa] ","1 ").replaceFirst("^Segund[oa] ","2 ").replaceFirst("^Terceir[oa] ","3 ");}
    static String group(String book){for(String[] g:GROUPS)for(int i=1;i<g.length;i++)if(g[i].equals(book))return g[0];return "";}
    static List<String> inGroup(String name){for(String[] g:GROUPS)if(g[0].equals(name)){List<String> out=new ArrayList<>(Arrays.asList(g).subList(1,g.length));out.removeIf(BibleReferences::deuterocanonical);return out;}return new ArrayList<>();}
    static String author(String book){for(String[] a:AUTHORS)for(int i=1;i<a.length;i++)if(a[i].equals(book))return a[0];return "";}

    final Random random;final Verses verses;final int level;final Set<String> used=new HashSet<>();
    BibleGame(int level,Random random,Verses verses){this.level=Math.max(EASY,Math.min(LEGEND,level));this.random=random;this.verses=verses;}

    /** Ten questions, mixed and never repeated within the round. */
    List<Question> round(){
        boolean bible=verses!=null&&verses.available();
        String[] plan;
        if(level==EASY)plan=bible?new String[]{"verse","verse","verse","verse","refverse","story","story","story","testament","fact"}:new String[]{"story","story","story","story","story","story","testament","testament","fact","fact"};
        else if(level==MEDIUM)plan=bible?new String[]{"verse","verse","chapter","chapter","free","free","refverse","chapters","first","story"}:new String[]{"story","story","story","chapters","chapters","first","first","fact","fact","order-next"};
        else if(level==HARD)plan=bible?new String[]{"blank","blank","blank","who","sequence","deep","deep","deep","chapter","free"}:new String[]{"blank","blank","blank","who","who","sequence","sequence","deep","deep","deep"};
        else plan=bible?new String[]{"deep","deep","deep","deep","blankwrite","blankwrite","sequence","who","free","chapter"}:new String[]{"deep","deep","deep","deep","deep","blankwrite","blankwrite","sequence","who","who"};
        List<Question> out=new ArrayList<>();
        String[] spare=level==EASY?new String[]{"story","testament","fact","order-next"}:level==MEDIUM?new String[]{"story","first","chapters","fact","order-next","group"}:level==HARD?new String[]{"deep","blank","who","sequence","story"}:new String[]{"deep","blankwrite","sequence","who","blank"};
        for(String kind:plan){Question q=null;for(int tries=0;tries<12&&q==null;tries++)q=make(kind);for(int s=0;q==null&&s<spare.length*3;s++)q=make(spare[s%spare.length]);if(q!=null)out.add(q);}
        Collections.shuffle(out,random);
        return out;
    }
    Question make(String kind){
        Question q;
        switch(kind){
            case "verse":q=verseToBook();break;
            case "refverse":q=referenceToVerse();break;
            case "chapter":q=bookAndChapter();break;
            case "free":q=writeTheBook();break;
            case "story":q=story();break;
            case "testament":q=testament();break;
            case "fact":q=fact();break;
            case "order-next":q=neighbour();break;
            case "group":q=groupQuestion();break;
            case "author":q=authorQuestion();break;
            case "chapters":q=chapterCount();break;
            case "blank":q=blank(false);break;
            case "blankwrite":q=blank(true);break;
            case "who":q=who();break;
            case "sequence":q=sequence();break;
            case "deep":q=deep();break;
            default:q=firstInOrder();
        }
        if(q==null||!used.add(q.prompt+"|"+q.body+"|"+q.answer))return null;
        return q;
    }
    <T> T pick(List<T> list){return list.get(random.nextInt(list.size()));}
    List<String> books(){return BibleReferences.books(false);}
    /** The right answer plus three different wrong ones, shuffled. */
    List<String> options(String right,List<String> pool){
        List<String> wrong=new ArrayList<>(new LinkedHashSet<>(pool));wrong.remove(right);Collections.shuffle(wrong,random);
        List<String> out=new ArrayList<>();out.add(right);for(String w:wrong){if(out.size()>=4)break;if(!out.contains(w))out.add(w);}
        if(out.size()<4)return null;Collections.shuffle(out,random);return out;
    }
    /** Wrong books for a level: other groups (easy), same testament (medium), same group first (hard). */
    List<String> distractors(String book){
        List<String> pool=new ArrayList<>();boolean nt=BibleReferences.newTestament(book);String g=group(book);
        if(level==EASY){for(String b:POPULAR)if(!group(b).equals(g))pool.add(b);}
        else if(level==-1){for(String b:POPULAR)if(BibleReferences.newTestament(b)==nt)pool.add(b);if(pool.size()<6)for(String b:books())if(BibleReferences.newTestament(b)==nt)pool.add(b);}
        else{List<String> same=inGroup(g);same.remove(book);Collections.shuffle(same,random);pool.addAll(same.subList(0,Math.min(3,same.size())));if(pool.size()<3)for(String b:books())if(BibleReferences.newTestament(b)==nt)pool.add(b);}
        pool.remove(book);return pool;
    }
    List<String> displayed(List<String> books){List<String> out=new ArrayList<>();for(String b:books)out.add(display(b));return out;}
    Verse verse(){
        if(verses==null||!verses.available())return null;
        if(level==EASY){for(int i=0;i<8;i++){BibleReferences.Reference r=BibleReferences.lookup(pick(Arrays.asList(FAMOUS)));if(r==null)continue;Verse v=verses.get(r.book,r.chapter,r.verse);if(v!=null&&v.text.length()>=20)return v;}return null;}
        List<Verse> found=verses.random(level==MEDIUM?Arrays.asList(POPULAR):null,3,random);
        for(Verse v:found)if(v.text.length()>=30&&v.text.length()<=280)return v;
        return found.isEmpty()?null:found.get(0);
    }
    static String clip(String text,int max){String t=text.trim().replaceAll("\\s+"," ");return t.length()<=max?t:t.substring(0,Math.max(0,t.lastIndexOf(' ',max))).trim()+"…";}
    Question verseToBook(){
        Verse v=verse();if(v==null)return null;
        List<String> o=options(display(v.book),displayed(distractors(v.book)));if(o==null)return null;
        return new Question("verse","Em que livro está esta passagem?","«"+clip(v.text,300)+"»",o,display(v.book),v.reference(),v.reference()+" · "+group(v.book));
    }
    Question writeTheBook(){
        Verse v=verse();if(v==null)return null;
        return new Question("free","Escreva ou diga o livro desta passagem","«"+clip(v.text,300)+"»",Collections.<String>emptyList(),v.book,v.reference(),v.reference()+" · "+group(v.book));
    }
    Question bookAndChapter(){
        Verse v=verse();if(v==null)return null;
        String right=display(v.book)+" "+v.chapter;List<String> pool=new ArrayList<>();int max=BibleReferences.chapters(v.book);
        for(int i=0;i<6&&max>1;i++){int c=1+random.nextInt(max);if(c!=v.chapter)pool.add(display(v.book)+" "+c);}
        for(String b:distractors(v.book)){int m=Math.max(1,BibleReferences.chapters(b));pool.add(display(b)+" "+(1+random.nextInt(m)));}
        List<String> mixed=new ArrayList<>();for(String p:pool)if(!mixed.contains(p)&&!p.equals(right))mixed.add(p);Collections.shuffle(mixed,random);
        List<String> wrong=new ArrayList<>();for(String p:mixed){if(wrong.size()>=3)break;if(p.startsWith(display(v.book)+" ")){if(wrong.size()<2)wrong.add(p);}else wrong.add(p);}
        for(String p:mixed){if(wrong.size()>=3)break;if(!wrong.contains(p))wrong.add(p);}
        List<String> o=options(right,wrong);if(o==null)return null;
        return new Question("chapter","Em que livro e capítulo está esta passagem?","«"+clip(v.text,300)+"»",o,right,v.reference(),v.reference());
    }
    Question referenceToVerse(){
        Verse v=verse();if(v==null)return null;
        List<Verse> others=verses.random(level>=MEDIUM?Collections.singletonList(v.book):Arrays.asList(POPULAR),8,random);
        List<String> pool=new ArrayList<>();for(Verse o:others)if(!(o.book.equals(v.book)&&o.chapter==v.chapter&&o.verse==v.verse)&&o.text.length()>=20)pool.add(clip(o.text,150));
        List<String> o=options(clip(v.text,150),pool);if(o==null)return null;
        return new Question("refverse","Qual destes textos é "+v.reference()+"?",v.reference(),o,clip(v.text,150),v.reference(),v.reference()+" · "+group(v.book));
    }
    Question story(){
        int want=level==EASY?0:level==MEDIUM?1:2;List<String[]> pool=new ArrayList<>();for(String[] s:STORIES){int l=Integer.parseInt(s[0]);if(l==want||level==MEDIUM&&l==2)pool.add(s);}
        if(pool.isEmpty())return null;String[] s=pick(pool);
        List<String> wrong=distractors(s[1]);for(String x:s[3].split(","))wrong.remove(x.trim());
        if(level>=HARD&&random.nextBoolean()||level==MEDIUM&&random.nextInt(3)==0)return new Question("free","Escreva ou diga o livro desta história",s[2],Collections.<String>emptyList(),s[1],"",display(s[1])+" · "+group(s[1]));
        List<String> o=options(display(s[1]),displayed(wrong));if(o==null)return null;
        return new Question("story","Em que livro está esta história?",s[2],o,display(s[1]),"",display(s[1])+" · "+group(s[1]));
    }
    Question testament(){
        String b=pick(Arrays.asList(POPULAR));String right=BibleReferences.newTestament(b)?"Novo Testamento":"Antigo Testamento";
        List<String> o=new ArrayList<>(Arrays.asList("Antigo Testamento","Novo Testamento"));
        return new Question("testament","Em que parte da Bíblia está este livro?",display(b),o,right,"",display(b)+" · "+group(b));
    }
    Question fact(){
        List<String[]> pool=new ArrayList<>();for(String[] f:FACTS)if(level==EASY?f[0].equals("0"):!f[0].equals("0"))pool.add(f);
        String[] f=pick(pool);List<String> o=options(f[2],Arrays.asList(f).subList(3,f.length));if(o==null)return null;
        return new Question("fact",f[1],"",o,f[2],"",f[2]);
    }
    Question neighbour(){
        List<String> all=books();int at=1+random.nextInt(all.size()-2);boolean after=random.nextBoolean();
        String b=all.get(at),right=all.get(after?at+1:at-1);
        List<String> pool=new ArrayList<>();for(int d=-3;d<=3;d++){int i=at+d;if(i>=0&&i<all.size()&&i!=at)pool.add(all.get(i));}
        List<String> o=options(display(right),displayed(pool));if(o==null)return null;
        return new Question("order",after?"Que livro vem logo a seguir?":"Que livro vem logo antes?",display(b),o,display(right),"",(after?display(b)+" → "+display(right):display(right)+" → "+display(b)));
    }
    Question groupQuestion(){
        String b=pick(books());String right=group(b);List<String> pool=new ArrayList<>();for(String[] g:GROUPS)pool.add(g[0]);
        List<String> o=options(right,pool);if(o==null)return null;
        return new Question("group","A que grupo de livros pertence?",display(b),o,right,"",display(b)+" · "+right);
    }
    Question authorQuestion(){
        String[] a=pick(Arrays.asList(AUTHORS));String b=a[1+random.nextInt(a.length-1)];
        List<String> o=options(a[0],Arrays.asList(PEOPLE));if(o==null)return null;
        return new Question("author","Segundo a tradição, quem escreveu este livro?",display(b),o,a[0],"",display(b)+" · "+a[0]);
    }
    Question chapterCount(){
        String b=pick(Arrays.asList(POPULAR));int n=BibleReferences.chapters(b);if(n<3)return null;
        Set<String> pool=new LinkedHashSet<>();int[] steps={1,2,3,4,5,6,8,10};
        while(pool.size()<6){int d=steps[random.nextInt(steps.length)]*(random.nextBoolean()?1:-1);int v=n+d;if(v>0&&v!=n)pool.add(""+v);}
        List<String> o=options(""+n,new ArrayList<>(pool));if(o==null)return null;
        return new Question("chapters","Quantos capítulos tem este livro?",display(b),o,""+n,"",display(b)+" · "+n+" capítulos");
    }
    Question firstInOrder(){
        List<String> all=books();List<String> four=new ArrayList<>();int base=random.nextInt(all.size()-12);
        while(four.size()<4){String b=all.get(base+random.nextInt(12));if(!four.contains(b))four.add(b);}
        String right=four.get(0);for(String b:four)if(all.indexOf(b)<all.indexOf(right))right=b;
        List<String> o=displayed(four);Collections.shuffle(o,random);
        return new Question("first","Qual destes livros aparece primeiro na Bíblia?","",o,display(right),"",display(right));
    }

    static final Set<String> COMMON=new HashSet<>(Arrays.asList("porque","quando","então","também","aquele","aquela","aqueles","estes","estas","esses","essas","sobre","entre","contra","depois","antes","ainda","todos","todas","nosso","nossa","vosso","vossa","minha","teus","tuas","seus","suas","assim","disse","estava","foram","serão","sejam","tinha","havia","sendo","nunca","mesmo","outro","outra","muito","pelos","pelas","desde","agora","diante","porém","senão","qualquer","quais","naquele","naquela","eles","elas","dele","dela","deles","delas","isto","isso","aquilo","vossos","nossos"));
    /** A verse with one important word missing: choose it (Difícil) or write it (Lendário). */
    Question blank(boolean write){
        boolean useBible=verses!=null&&verses.available()&&random.nextInt(3)>0;
        if(useBible){
            Verse v=verse();
            if(v!=null){
                String[] words=v.text.trim().split("\\s+");List<Integer> good=new ArrayList<>();
                for(int i=0;i<words.length;i++){String w=words[i].replaceAll("[^\\p{L}]","");if(w.length()>=5&&!COMMON.contains(w.toLowerCase(Locale.ROOT)))good.add(i);}
                if(words.length>=8&&!good.isEmpty()){
                    int at=good.get(random.nextInt(good.size()));String word=words[at].replaceAll("[^\\p{L}]","");
                    StringBuilder b=new StringBuilder();for(int i=0;i<words.length;i++){if(i>0)b.append(' ');b.append(i==at?words[i].replace(word,"____"):words[i]);}
                    String body="«"+clip(b.toString(),320)+"»";
                    if(write)return new Question("blankwrite","Escreva a palavra que falta",body,Collections.<String>emptyList(),word,v.reference(),v.reference()+" · «"+clip(v.text,200)+"»");
                    List<String> pool=new ArrayList<>();
                    for(Verse o:verses.random(null,6,random))for(String w:o.text.split("\\s+")){String c=w.replaceAll("[^\\p{L}]","");if(c.length()>=Math.max(4,word.length()-3)&&c.length()<=word.length()+3&&!COMMON.contains(c.toLowerCase(Locale.ROOT))&&!c.equalsIgnoreCase(word))pool.add(Character.isUpperCase(word.charAt(0))?c:c.toLowerCase(Locale.ROOT));}
                    List<String> o=options(word,pool);
                    if(o!=null)return new Question("blank","Complete a passagem",body,o,word,v.reference(),v.reference());
                }
            }
        }
        List<String[]> pool=new ArrayList<>();for(String[] x:BLANKS){int l=Integer.parseInt(x[0]);if(write||l==Math.min(level,LEGEND)||level==HARD&&l==3&&random.nextInt(3)==0)pool.add(x);}
        if(pool.isEmpty())return null;String[] x=pick(pool);String ref=x[x.length-1];
        if(write)return new Question("blankwrite","Escreva a palavra que falta","«"+x[1]+"» — "+ref,Collections.<String>emptyList(),x[2],ref,ref+" · «"+x[1].replace("____",x[2])+"»");
        List<String> o=options(x[2],Arrays.asList(x).subList(3,x.length-1));if(o==null)return null;
        return new Question("blank","Complete a passagem","«"+x[1]+"» — "+ref,o,x[2],ref,ref+" · «"+x[1].replace("____",x[2])+"»");
    }
    Question who(){
        List<String[]> pool=new ArrayList<>();for(String[] q:QUOTES){int l=Integer.parseInt(q[0]);if(l==Math.min(level,LEGEND)||level==LEGEND&&l==2&&random.nextInt(4)==0)pool.add(q);}
        if(pool.isEmpty())return null;String[] q=pick(pool);
        List<String> o=options(q[2],Arrays.asList(q).subList(3,q.length-1));if(o==null)return null;
        return new Question("who","Quem disse estas palavras?",q[1],o,q[2],q[q.length-1],q[q.length-1]+" · "+q[2]);
    }
    /** Which of four events came first (Difícil) or last (Lendário, with closer events). */
    Question sequence(){
        int span=level>=LEGEND?6:10;int base=random.nextInt(EVENTS.length-span+1);List<Integer> idx=new ArrayList<>();
        while(idx.size()<4){int i=base+random.nextInt(span);if(!idx.contains(i))idx.add(i);}
        boolean last=level>=LEGEND;int right=idx.get(0);for(int i:idx)if(last?i>right:i<right)right=i;
        List<String> o=new ArrayList<>();for(int i:idx)o.add(EVENTS[i]);Collections.shuffle(o,random);
        List<Integer> sorted=new ArrayList<>(idx);Collections.sort(sorted);StringBuilder order=new StringBuilder();for(int i:sorted){if(order.length()>0)order.append(" → ");order.append(EVENTS[i]);}
        return new Question("sequence",last?"Qual destes acontecimentos foi o último?":"Qual destes acontecimentos aconteceu primeiro?","",o,EVENTS[right],"",order.toString());
    }
    /** Knowledge that needs reasoning (Difícil) or a question to meditate on (Lendário); the explanation is shown after answering. */
    Question deep(){
        List<String[]> pool=new ArrayList<>();for(String[] q:DEEP)if(Integer.parseInt(q[0])==Math.min(level,LEGEND))pool.add(q);
        if(pool.isEmpty())return null;String[] q=pick(pool);
        List<String> o=options(q[2],Arrays.asList(q).subList(3,q.length-1));if(o==null)return null;
        return new Question(level>=LEGEND?"reflect":"deep",q[1],"",o,q[2],"",q[q.length-1]);
    }

    /** Points for one answer: level base, time bonus and a streak bonus; help halves it. */
    static int points(int level,boolean correct,int secondsLeft,int streak,boolean helped){
        if(!correct)return 0;int total=POINTS[level]+Math.max(0,secondsLeft)*(level+1)*2;
        if(streak>=3)total=Math.round(total*1.2f);if(helped)total/=2;return total;
    }
    static int stars(int correct){return correct>=9?3:correct>=7?2:correct>=5?1:0;}
    /** Two wrong options to hide with the 50/50 help. */
    List<String> fifty(Question q){List<String> wrong=new ArrayList<>(q.options);wrong.remove(q.answer);Collections.shuffle(wrong,random);return wrong.subList(0,Math.min(2,wrong.size()));}
}
