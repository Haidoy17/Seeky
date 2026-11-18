package mz.bam.seeky.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Fronteira de confiança para dados que chegam de outras aplicações.
 *
 * <p>O Seeky expõe actividades de partilha/abertura (IncomingActivity) e o lançador
 * (MainActivity). Sem filtros, outra aplicação podia pedir ao Seeky que lesse em
 * seu nome:
 * <ul>
 *   <li>URIs {@code content://} dos providers internos do próprio Seeky (materiais de
 *   estudo, ficheiros de leitura), que o Seeky consegue abrir mas o chamador não;</li>
 *   <li>URIs {@code file://} da área privada da aplicação (bases de dados,
 *   preferências, cofre de chaves), que eram copiados sem verificação para a
 *   biblioteca ou para uma pasta pública.</li>
 * </ul>
 * Este padrão é conhecido como "confused deputy". As regras aqui centralizam a
 * validação para que todos os pontos de entrada usem a mesma política.
 */
final class IncomingUris {
    /** Extra que prova que um Intent foi criado dentro do Seeky. */
    static final String TOKEN_EXTRA = "mz.bam.seeky.internal_token";
    private static final String PREFS = "seeky_internal_launch";
    private static volatile String cachedToken;

    private IncomingUris() {}

    /** Verdadeiro quando um URI recebido de outra aplicação pode ser lido pelo Seeky em seu nome. */
    static boolean acceptable(Context c, Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if ("content".equals(scheme)) return !ownAuthority(c, uri.getAuthority());
        if ("file".equals(scheme)) {
            String path = uri.getPath();
            // Só ficheiros em volumes de armazenamento partilhado já autorizados pelo utilizador;
            // StorageAccess.allowed resolve o caminho canónico (bloqueia "..", ligações e /data).
            return path != null && !path.isEmpty() && StorageAccess.allowed(c, new File(path));
        }
        return false;
    }

    /** Autoridades do próprio pacote (incluindo o prefixo multiutilizador "10@"). */
    static boolean ownAuthority(Context c, String authority) {
        if (authority == null || authority.isEmpty()) return true;
        return ownAuthority(c.getPackageName(), authority);
    }

    static boolean ownAuthority(String packageName, String authority) {
        if (authority == null || authority.isEmpty()) return true;
        String a = authority.toLowerCase(Locale.ROOT);
        int at = a.lastIndexOf('@');
        if (at >= 0) a = a.substring(at + 1);
        String pkg = packageName.toLowerCase(Locale.ROOT);
        return a.equals(pkg) || a.startsWith(pkg + ".");
    }

    /** Marca um Intent criado pelo Seeky para que o destino possa confiar nos seus extras. */
    static Intent sign(Context c, Intent intent) {
        return intent.putExtra(TOKEN_EXTRA, token(c));
    }

    /** Verdadeiro só para Intents assinados com {@link #sign}; o valor é secreto e privado da aplicação. */
    static boolean trusted(Context c, Intent intent) {
        if (intent == null) return false;
        String received;
        try {
            received = intent.getStringExtra(TOKEN_EXTRA);
        } catch (RuntimeException malformed) {
            return false;
        }
        if (received == null) return false;
        try {
            return MessageDigest.isEqual(received.getBytes("UTF-8"), token(c).getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException impossible) {
            return false;
        }
    }

    private static String token(Context c) {
        String value = cachedToken;
        if (value != null) return value;
        synchronized (IncomingUris.class) {
            if (cachedToken != null) return cachedToken;
            SharedPreferences prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            value = prefs.getString("token", null);
            if (value == null || value.length() < 32) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                StringBuilder hex = new StringBuilder(64);
                for (byte b : random) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
                value = hex.toString();
                prefs.edit().putString("token", value).commit();
            }
            cachedToken = value;
            return value;
        }
    }
}
