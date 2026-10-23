import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = (await readFile(new URL('../src/lib/api.ts', import.meta.url), 'utf8')).replaceAll(
  'import.meta.env',
  '({ VITE_DEMO_MODE: "false" })',
);
const js = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText;
const storage = new Map();
globalThis.sessionStorage = {
  getItem: (k) => storage.get(k) ?? null,
  setItem: (k, v) => storage.set(k, v),
  removeItem: (k) => storage.delete(k),
};
globalThis.window = new EventTarget();
const api = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'));
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
test('unwraps repository RestResult and accepts raw documentation JSON', async () => {
  globalThis.fetch = async () => json({ code: 200, data: { public_id: 'user_1' } });
  assert.equal((await api.request('/users/me')).public_id, 'user_1');
  globalThis.fetch = async () => json([{ public_id: 'friend_1' }]);
  assert.equal((await api.request('/friends'))[0].public_id, 'friend_1');
});
test('rotates tokens once for concurrent expired requests and retries with new token', async () => {
  storage.clear();
  api.tokens({ access_token: 'old', refresh_token: 'refresh-old' });
  let rotations = 0;
  globalThis.fetch = async (url, opts) => {
    if (url.endsWith('/auth/refresh')) {
      rotations++;
      await new Promise((r) => setTimeout(r, 5));
      assert.equal(opts.headers.Authorization, 'Bearer old');
      assert.equal(JSON.parse(opts.body).refresh_token, 'refresh-old');
      return json({ code: 200, data: { access_token: 'new', refresh_token: 'refresh-new' } });
    }
    return opts.headers.get('Authorization') === 'Bearer new'
      ? json({ code: 200, data: { ok: true } })
      : json({ error_code: 'TOKEN_EXPIRED' }, 401);
  };
  const result = await Promise.all([api.request('/friends'), api.request('/users/me')]);
  assert.equal(rotations, 1);
  assert.ok(result.every((r) => r.ok));
  assert.equal(storage.get('refresh_token'), 'refresh-new');
});
test('SSE parser preserves Chinese split across byte chunks and CRLF events', async () => {
  const bytes = new TextEncoder().encode(
    'data: {"output":"你好"}\r\n\r\ndata: {"output":"，朋友"}\n\ndata: [DONE]\n\n',
  );
  globalThis.fetch = async () =>
    new Response(
      new ReadableStream({
        start(c) {
          for (let i = 0; i < bytes.length; i += 3) c.enqueue(bytes.slice(i, i + 3));
          c.close();
        },
      }),
      { headers: { 'Content-Type': 'text/event-stream' } },
    );
  let result = '';
  await api.stream(
    '/ai/interactions/polish',
    {},
    (chunk) => (result += chunk),
    new AbortController().signal,
  );
  assert.equal(result, '你好，朋友');
});
test('204 and invalid session are handled without parsing an empty body', async () => {
  globalThis.fetch = async () => new Response(null, { status: 204 });
  assert.equal(await api.request('/schedules/1', { method: 'DELETE' }), null);
  globalThis.fetch = async () => json({ errorCode: 'TOKEN_INVALID', msg: '请重新登录' }, 401);
  await assert.rejects(api.request('/users/me'), /请重新登录/);
  assert.equal(storage.get('access_token'), undefined);
});
test('AI streaming refreshes an expired bearer token before reading output', async () => {
  api.tokens({ access_token: 'expired-stream', refresh_token: 'stream-refresh' });
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/auth/refresh'))
      return json({ code: 200, data: { access_token: 'stream-new', refresh_token: 'rotated' } });
    if (options.headers.get('Authorization') !== 'Bearer stream-new')
      return json({ errorCode: 'TOKEN_EXPIRED' }, 401);
    return new Response('data: {"output":"已刷新"}\n\n', {
      headers: { 'Content-Type': 'text/event-stream' },
    });
  };
  let output = '';
  await api.stream(
    '/ai/interactions/polish',
    {},
    (text) => (output += text),
    new AbortController().signal,
  );
  assert.equal(output, '已刷新');
  assert.equal(storage.get('refresh_token'), 'rotated');
});
test('HTTP 200 business errors without data are rejected instead of treated as success', async () => {
  globalThis.fetch = async () =>
    json({ code: 400, msg: '你们已经是好友', error_code: 'FRIENDSHIP_BUSINESS' });
  await assert.rejects(api.request('/friends/requests', { method: 'POST' }), /你们已经是好友/);
  globalThis.fetch = async () =>
    json({ code: 400, msg: '模型未配置', error_code: 'MODEL_OPTION_INVALID' });
  await assert.rejects(
    api.stream('/ai/interactions/polish', {}, () => {}, new AbortController().signal),
    /模型未配置/,
  );
});
test('HTTP 200 TOKEN_EXPIRED responses are refreshed and retried', async () => {
  api.tokens({ access_token: 'business-old', refresh_token: 'business-refresh' });
  globalThis.fetch = async (url, options) =>
    url.endsWith('/auth/refresh')
      ? json({ code: 200, data: { access_token: 'business-new', refresh_token: 'business-next' } })
      : options.headers.get('Authorization') === 'Bearer business-new'
        ? json({ code: 200, data: { ok: true } })
        : json({ code: 401, msg: 'expired', error_code: 'TOKEN_EXPIRED' });
  assert.equal((await api.request('/users/me')).ok, true);
});
