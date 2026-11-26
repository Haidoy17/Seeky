// Seeky IA — serviço sem chave para as aplicações Seeky (Android e Windows).
// Guarda a chave do Google Gemini como segredo (GEMINI_KEY) e reencaminha só pedidos generateContent,
// com modelos permitidos, tamanho limitado e limites diários por instalação, por endereço e no total.
const MAX_BODY = 200_000;          // caracteres de JSON por pedido (texto; sem áudio grande)
const MAX_OUTPUT = 4096;           // tokens de resposta no máximo

function reply(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' } });
}
function error(code, status, message) { return reply({ error: { code, status, message } }, code); }

async function count(env, key, limit) {
  const n = Number((await env.LIMITS.get(key)) || 0);
  if (n >= limit) return false;
  await env.LIMITS.put(key, String(n + 1), { expirationTtl: 2 * 86400 });
  return true;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === 'GET' && url.pathname === '/') return reply({ ok: true, service: 'Seeky IA' });
    const match = url.pathname.match(/^\/v1beta\/models\/([a-z0-9][a-z0-9.\-]{2,63}):generateContent$/);
    if (request.method !== 'POST' || !match) return error(404, 'NOT_FOUND', 'Not found');
    const model = match[1];
    const allowed = (env.MODELS || 'gemini-3.5-flash-lite,gemini-3.5-flash').split(',').map(s => s.trim()).filter(Boolean);
    // A model that is not offered looks "not found" to the app, which then tries the next one.
    if (!allowed.includes(model)) return error(404, 'NOT_FOUND', `models/${model} is not found or is not supported for generateContent`);
    const install = request.headers.get('X-Seeky-Install') || '';
    if (!/^[a-f0-9]{32}$/.test(install)) return error(403, 'PERMISSION_DENIED', 'Seeky IA: pedido sem identificação da aplicação.');
    if (Number(request.headers.get('content-length') || 0) > MAX_BODY) return error(413, 'INVALID_ARGUMENT', 'Seeky IA: pedido demasiado grande.');
    const raw = await request.text();
    if (raw.length > MAX_BODY) return error(413, 'INVALID_ARGUMENT', 'Seeky IA: pedido demasiado grande.');
    let body; try { body = JSON.parse(raw); } catch { return error(400, 'INVALID_ARGUMENT', 'Seeky IA: pedido inválido.'); }
    if (!Array.isArray(body.contents) || body.contents.length === 0 || body.contents.length > 20) return error(400, 'INVALID_ARGUMENT', 'Seeky IA: pedido inválido.');
    // Only what the apps send: the conversation, the instructions and a bounded configuration. No tools, no files.
    const config = body.generationConfig || {};
    const clean = {
      contents: body.contents,
      ...(body.systemInstruction ? { systemInstruction: body.systemInstruction } : {}),
      generationConfig: {
        temperature: Math.min(1, Math.max(0, Number(config.temperature ?? 0.35))),
        maxOutputTokens: Math.min(MAX_OUTPUT, Math.max(16, Number(config.maxOutputTokens ?? 2048))),
        ...(config.thinkingConfig && typeof config.thinkingConfig.thinkingLevel === 'string' ? { thinkingConfig: { thinkingLevel: config.thinkingConfig.thinkingLevel.slice(0, 16) } } : {}),
      },
    };
    const day = new Date().toISOString().slice(0, 10);
    const ip = request.headers.get('CF-Connecting-IP') || 'desconhecido';
    const perInstall = Number(env.PER_INSTALL || 40), perIp = Number(env.PER_IP || 120), total = Number(env.PER_DAY || 1000);
    if (!(await count(env, `t:${day}`, total))) return error(429, 'RESOURCE_EXHAUSTED', 'Seeky IA: o limite diário do serviço foi atingido. Tente amanhã ou use a sua própria chave.');
    if (!(await count(env, `a:${day}:${ip}`, perIp))) return error(429, 'RESOURCE_EXHAUSTED', 'Seeky IA: limite diário desta rede atingido. Tente amanhã ou use a sua própria chave.');
    if (!(await count(env, `i:${day}:${install}`, perInstall))) return error(429, 'RESOURCE_EXHAUSTED', 'Seeky IA: limite diário deste aparelho atingido. Tente amanhã ou use a sua própria chave.');
    const google = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: 'POST', headers: { 'content-type': 'application/json', 'x-goog-api-key': env.GEMINI_KEY }, body: JSON.stringify(clean),
    });
    const text = await google.text();
    // Google's errors go back as they are (without the key, which Google never echoes); the app explains them.
    return new Response(text.length > 400_000 ? '{"error":{"code":502,"status":"UNAVAILABLE","message":"Resposta demasiado grande"}}' : text,
      { status: google.status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' } });
  },
};
