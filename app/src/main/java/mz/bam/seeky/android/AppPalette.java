package mz.bam.seeky.android;
import android.content.Context;
final class AppPalette {
 /** "Sistema" segue o modo claro/escuro do Android (Claro ou Escuro). */
 static final String SYSTEM="Sistema";static final String SCHEDULE="Horário";
 /** Temas modernos: cada um com cores, cabeçalho, tipografia, ícones e capas próprios. */
 static final String SAND="Areia",FOREST="Floresta",CHARCOAL="Carvão",AURORA="Aurora",GOLD="Ouro",NEON="Néon",GLASS="Vidro",ICE="Gelo",CYAN="Ciano",DEFAULT=SAND;
 /** Temas das paletas enviadas pelo autor (5.35). */
 static final String SLATE="Ardósia",SAGE="Sálvia",MIST="Névoa",MINT_TEAL="Hortelã",PINE="Pinheiro",MINT="Menta",PETROL="Petróleo",QUARTZ="Quartzo",LEAF="Folha",PRODUCTIVE="Produtivo";
 static final String[] PALETTES={SLATE,SAGE,MIST,MINT_TEAL,PINE,MINT,PETROL,QUARTZ,LEAF,PRODUCTIVE};
 static final String[] MODERN={SAND,FOREST,AURORA,GOLD,NEON,GLASS,ICE,CHARCOAL,CYAN,SLATE,SAGE,MIST,MINT_TEAL,PINE,MINT,PETROL,QUARTZ,LEAF,PRODUCTIVE};
 /** Descrição curta de cada tema moderno, mostrada no selector de cores. */
 static String describe(String name){switch(name){case SAND:return "creme e oliva, editorial";case FOREST:return "verde floresta, escuro";case AURORA:return "bordô e cinza, papel";case GOLD:return "grafite e mostarda";case NEON:return "preto com néon laranja";case GLASS:return "vidro escuro e violeta";case ICE:return "cinza claro minimalista";case CHARCOAL:return "grafite e coral";case CYAN:return "ardósia e ciano";case SLATE:return "ardósia e coral, cartões suaves";case SAGE:return "verde-sálvia e creme, sereno";case MIST:return "azul empoeirado e rosa suave";case MINT_TEAL:return "azul-petróleo e hortelã";case PINE:return "verde-pinheiro e cinza claro";case MINT:return "verde menta e esmeralda";case PETROL:return "petróleo escuro e pêssego";case QUARTZ:return "verde-quartzo e rosado";case LEAF:return "verde-folha, claro e arejado";case PRODUCTIVE:return "azul-petróleo e laranja";case SYSTEM:return "Areia ou Floresta, conforme o Android";case SCHEDULE:return "Areia de dia (7h–19h), Floresta à noite";default:return "";}}
 /** Estilo de ícone: 0 quadrado tingido, 1 contorno, 2 sem caixa, 3 círculo. */
 static final int TILE_TINT=0,TILE_OUTLINE=1,TILE_NONE=2,TILE_CIRCLE=3;
 static final String[] NAMES={SYSTEM,SCHEDULE,SAND,FOREST,AURORA,GOLD,NEON,GLASS,ICE,CHARCOAL,CYAN,SLATE,SAGE,MIST,MINT_TEAL,PINE,MINT,PETROL,QUARTZ,LEAF,PRODUCTIVE,"Claro","Escuro","Jade","Oceano","Coral","Âmbar","Rosa","Grafite","Azul royal","Turquesa","Esmeralda","Laranja","Vinho","Lavanda","Céu","Noite"};
 final int bg,surface,ink,muted,accent,line,tint,heroStart,heroEnd,onAccent;final boolean light,modern;
 /** Identidade do tema moderno ("areia", "aurora", "neon"…; "classic" nos restantes), segunda cor dos cartões empilhados, raio dos cartões e estilo dos ícones. */
 final String style;final int secondary,radius,tile;
 /** Cabeçalho sem barra: logótipo e informação directamente sobre o fundo (Areia, Floresta, Néon, Vidro, Gelo). */
 final boolean bare;
 AppPalette(String name){if(java.util.Arrays.asList(MODERN).contains(name)){modern=true;int r=18,t=TILE_TINT,sec=0;
  switch(name){
   case SAND:style="areia";light=true;bg=0xfff3eee6;surface=0xfffbf8f2;ink=0xff23251f;muted=0xff686358;line=0xffe2d9cb;accent=0xff3f4b3a;onAccent=0xffffffff;heroStart=0xff3f4b3a;heroEnd=0xff2c3528;sec=0xffb08d5e;break;
   case FOREST:style="floresta";light=false;bg=0xff161d18;surface=0xff212a23;ink=0xffeef2ea;muted=0xffa9b4a8;line=0xff334036;accent=0xffb5cda3;onAccent=0xff161d18;heroStart=0xff2b3a2f;heroEnd=0xff1b241e;sec=0xff3d5244;r=22;break;
   case AURORA:style="aurora";light=true;bg=0xffe8e3e0;surface=0xfff4f1ef;ink=0xff2a2426;muted=0xff655c5f;line=0xffd9d1cd;accent=0xff7a2e43;onAccent=0xffffffff;heroStart=0xff752b40;heroEnd=0xff5c2133;sec=0xff8d8987;r=26;break;
   case GOLD:style="ouro";light=false;bg=0xff1d1d1d;surface=0xff282828;ink=0xfff2efe6;muted=0xffb3ada0;line=0xff3a3936;accent=0xffd1ad45;onAccent=0xff1d1d1d;heroStart=0xff2c2c2c;heroEnd=0xff222222;sec=0xffc6a23c;r=26;break;
   case NEON:style="neon";light=false;bg=0xff121212;surface=0xff1c1c1d;ink=0xfff4f4f4;muted=0xffa8a8a8;line=0xff343436;accent=0xffff7a2f;onAccent=0xff121212;heroStart=0xff1a1a1b;heroEnd=0xff111111;sec=0xffff7a2f;r=14;t=TILE_OUTLINE;break;
   case GLASS:style="vidro";light=false;bg=0xff111116;surface=0xff1b1b22;ink=0xfff1f0f5;muted=0xffa5a3b0;line=0xff2c2c36;accent=0xffb08cff;onAccent=0xff111116;heroStart=0xff22202b;heroEnd=0xff121218;sec=0xff8b5cf6;r=20;t=TILE_NONE;break;
   case ICE:style="gelo";light=true;bg=0xffebebee;surface=0xfff8f8f9;ink=0xff111113;muted=0xff5f5f66;line=0xffd9d9de;accent=0xff111113;onAccent=0xffffffff;heroStart=0xff161618;heroEnd=0xff0b0b0c;sec=0xffffffff;r=22;t=TILE_NONE;break;
   case SLATE:style="ardosia";light=false;bg=0xff3a3e48;surface=0xff474c58;ink=0xfff4f5f7;muted=0xffc9ccd3;line=0xff595e6b;accent=0xfff4a088;onAccent=0xff2a2f38;heroStart=0xff565c69;heroEnd=0xff3d424c;sec=0xff86d1a8;r=24;t=TILE_CIRCLE;break;
   case SAGE:style="salvia";light=true;bg=0xfff0efe6;surface=0xfffbfaf4;ink=0xff23302f;muted=0xff53625f;line=0xffdcdacb;accent=0xff405a5c;onAccent=0xffffffff;heroStart=0xff587673;heroEnd=0xff405a5c;sec=0xffc3c1af;r=28;break;
   case MIST:style="nevoa";light=true;bg=0xfff4f1f4;surface=0xffffffff;ink=0xff283041;muted=0xff5b6578;line=0xffe4dde3;accent=0xff5b6c88;onAccent=0xffffffff;heroStart=0xff63738f;heroEnd=0xff4f5f7b;sec=0xffe4d8dc;r=26;t=TILE_CIRCLE;break;
   case MINT_TEAL:style="hortela";light=true;bg=0xffe7f0e5;surface=0xffffffff;ink=0xff17353b;muted=0xff4a6569;line=0xffcde0c9;accent=0xff2c6975;onAccent=0xffffffff;heroStart=0xff2c6975;heroEnd=0xff1f4f58;sec=0xff68b2a0;r=24;break;
   case PINE:style="pinheiro";light=true;bg=0xffe8eae5;surface=0xffffffff;ink=0xff10241c;muted=0xff4f5c55;line=0xffd5d9d2;accent=0xff00412e;onAccent=0xffffffff;heroStart=0xff00412e;heroEnd=0xff002c1f;sec=0xff96bf8a;r=18;break;
   case MINT:style="menta";light=true;bg=0xffeef8f0;surface=0xffffffff;ink=0xff1b4332;muted=0xff466556;line=0xffd8f3dc;accent=0xff2d6a4f;onAccent=0xffffffff;heroStart=0xff1b4332;heroEnd=0xff2d6a4f;sec=0xff74c69d;r=20;t=TILE_CIRCLE;break;
   case PETROL:style="petroleo";light=false;bg=0xff384d4b;surface=0xff455e5b;ink=0xfff4f7f6;muted=0xffcbd8d5;line=0xff587270;accent=0xfff7c9a1;onAccent=0xff2b3a39;heroStart=0xff4f6966;heroEnd=0xff384d4b;sec=0xffffffff;r=26;break;
   case QUARTZ:style="quartzo";light=true;bg=0xfff4efed;surface=0xffffffff;ink=0xff27322f;muted=0xff5f6b69;line=0xffe6dfdc;accent=0xff4a766e;onAccent=0xffffffff;heroStart=0xff4f7d75;heroEnd=0xff3b6058;sec=0xffe9e1de;r=18;break;
   case LEAF:style="folha";light=true;bg=0xffeef5f0;surface=0xffffffff;ink=0xff1c2a22;muted=0xff56665c;line=0xffdbe8de;accent=0xff4d7d5d;onAccent=0xffffffff;heroStart=0xff52805f;heroEnd=0xff416b4e;sec=0xffe1efe5;r=22;break;
   case PRODUCTIVE:style="produtivo";light=true;bg=0xfff3f5f6;surface=0xffffffff;ink=0xff1c3742;muted=0xff56676f;line=0xffe3e8eb;accent=0xffd0801a;onAccent=0xff10141d;heroStart=0xff1f4e5f;heroEnd=0xff163a47;sec=0xfff5a83d;r=26;t=TILE_CIRCLE;break;
   case CYAN:style="ciano";light=false;bg=0xff272c37;surface=0xff313744;ink=0xffeef3f8;muted=0xffaab4c2;line=0xff414959;accent=0xff3fd3f2;onAccent=0xff0f222b;heroStart=0xff1d97bd;heroEnd=0xff2559c9;sec=0xff3fd3f2;r=20;t=TILE_CIRCLE;break;
   default:style="carvao";light=false;bg=0xff1b1b1c;surface=0xff262627;ink=0xfff2f2f2;muted=0xffababab;line=0xff3a3a3b;accent=0xffe06b60;onAccent=0xff1b1b1c;heroStart=0xff252526;heroEnd=0xff1b1b1c;sec=0xffe06b60;r=16;t=TILE_OUTLINE;break;}
  bare=style.equals("areia")||style.equals("floresta")||style.equals("neon")||style.equals("vidro")||style.equals("gelo");
  secondary=sec;radius=r;tile=t;tint=ThemeColors.blend(surface,accent,light?.08f:.13f);return;}
  style="classic";secondary=0;radius=16;tile=TILE_TINT;bare=false;
  modern=false;light=!name.equals("Escuro")&&!name.equals("Jade")&&!name.equals("Grafite")&&!name.equals("Noite");int a=name.equals("Azul royal")?0xff2553c7:name.equals("Turquesa")?0xff007f83:name.equals("Esmeralda")?0xff167347:name.equals("Laranja")?0xffb84a0e:name.equals("Vinho")?0xffa12550:name.equals("Lavanda")?0xff7352ae:name.equals("Céu")?0xff0875a4:name.equals("Noite")?0xffa8bcff:name.equals("Oceano")?0xff0369a1:name.equals("Coral")?0xffc24136:name.equals("Âmbar")?0xff956000:name.equals("Rosa")?0xffb52169:name.equals("Jade")?0xff66dab0:name.equals("Grafite")?0xffa2b8d3:name.equals("Escuro")?0xffb9a5ff:0xff7c13d9;accent=a;bg=light?ThemeColors.blend(0xffffffff,accent,.035f):name.equals("Jade")?0xff0c1c18:0xff10141d;surface=light?0xffffffff:name.equals("Jade")?0xff172d26:0xff1b2230;ink=light?0xff1c2536:0xffedf2fa;muted=light?0xff59677a:0xffaab8cc;line=light?0xffdce3ed:0xff303c4e;tint=ThemeColors.blend(surface,accent,light?.055f:.095f);onAccent=light?0xffffffff:0xff111821;heroStart=ThemeColors.hero(accent,light,bg);heroEnd=ThemeColors.blend(heroStart,0xff000000,.24f);}
 AppPalette(int requested,int background){modern=false;style="classic";secondary=0;radius=16;tile=TILE_TINT;bare=false;bg=background|0xff000000;light=contrast(bg,0xff000000)>=contrast(bg,0xffffffff);surface=ThemeColors.blend(bg,light?0xffffffff:0xff141a24,.82f);ink=light?0xff000000:0xffffffff;int secondary=ThemeColors.blend(ink,surface,.30f);for(int i=0;i<30&&(contrast(secondary,bg)<4.5||contrast(secondary,surface)<4.5);i++)secondary=ThemeColors.blend(secondary,ink,.13f);muted=contrast(secondary,bg)>=4.5&&contrast(secondary,surface)>=4.5?secondary:ink;int candidate=requested|0xff000000;for(int i=0;i<30&&(contrast(candidate,bg)<4.5||contrast(candidate,surface)<4.5);i++)candidate=ThemeColors.blend(candidate,ink,.13f);if(contrast(candidate,bg)<4.5||contrast(candidate,surface)<4.5)candidate=ink;accent=candidate;line=ThemeColors.blend(surface,ink,.16f);tint=ThemeColors.blend(surface,accent,.08f);onAccent=contrast(accent,0xffffffff)>=4.5?0xffffffff:0xff000000;int hero=ThemeColors.hero(accent,light,bg);for(int i=0;i<30&&contrast(hero,0xffffffff)<4.5;i++)hero=ThemeColors.blend(hero,0xff000000,.12f);heroStart=hero;heroEnd=ThemeColors.blend(hero,0xff000000,.24f);}
 static double luminance(int c){double total=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double channel=((c>>(16-i*8))&255)/255.;total+=weights[i]*(channel<=.04045?channel/12.92:Math.pow((channel+.055)/1.055,2.4));}return total;}
 static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 /** Cor do logótipo e do texto do cabeçalho: tinta do tema sem barra, branco sobre barra colorida. */
 int headerInk(){return bare?ink:0xffffffff;}
 int headerMuted(){return bare?muted:0xddffffff;}
 /** Tema Horário: Areia das 7h às 19h, Floresta no resto do dia. */
 static boolean daytime(long now){java.util.Calendar cal=java.util.Calendar.getInstance();cal.setTimeInMillis(now);int h=cal.get(java.util.Calendar.HOUR_OF_DAY);return h>=7&&h<19;}
 static boolean night(Context c){return (c.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;}
 static AppPalette of(Context c){android.content.SharedPreferences p=c.getSharedPreferences("seeky",0);String name=p.getString("theme",DEFAULT);
if(SYSTEM.equals(name))name=night(c)?FOREST:SAND;else if(SCHEDULE.equals(name))name=daytime(System.currentTimeMillis())?SAND:FOREST;return name.equals("Personalizado")?new AppPalette(p.getInt("theme_custom_accent",0xff7c13d9),p.getInt("theme_custom_background",0xfffaf8ff)):new AppPalette(name);}
}
