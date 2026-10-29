import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import net from 'node:net';
const accounts = JSON.parse(
  await readFile(new URL('../.local/accounts.json', import.meta.url), 'utf8'),
);
const base = process.env.AUDIT_API_BASE || 'http://127.0.0.1:8082/api/v1',
  records = [];
async function call(
  actor,
  path,
  method = 'GET',
  body,
  expected = [200, 201, 202, 204],
  kind = 'json',
) {
  const headers = {};
  if (actor?.access_token) headers.Authorization = 'Bearer ' + actor.access_token;
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const start = Date.now();
  let data, status, http;
  try {
    const r = await fetch(base + path, {
      method,
      headers,
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(55000),
    });
    http = r.status;
    const raw = await r.text();
    try {
      data = JSON.parse(raw);
    } catch {
      data = raw;
    }
    status = data?.code >= 400 ? data.code : r.status;
    const pass = expected.includes(status);
    records.push({
      method,
      path,
      http_status: http,
      business_status: status,
      pass,
      elapsed_ms: Date.now() - start,
      ...(!pass ? { response: typeof data === 'string' ? data.slice(0, 1200) : data } : {}),
    });
    console.log(method, path, http, status, pass ? 'PASS' : 'FAIL');
    return { data: data?.data ?? data, status, pass, raw };
  } catch (e) {
    records.push({ method, path, pass: false, error: e.message, elapsed_ms: Date.now() - start });
    console.log(method, path, 'ERROR', e.message);
    return { data: null, pass: false };
  }
}
for (const a of accounts) {
  Object.assign(
    a,
    (await call(null, '/auth/login', 'POST', { username: a.username, password: a.password })).data,
  );
  a.public_id = (await call(a, '/users/me')).data.public_id;
}
const [a, b, c] = accounts;
await call(null, '/users/me', 'GET', undefined, [401]);
await call(
  null,
  '/auth/login',
  'POST',
  { username: a.username, password: 'WrongPassword1!' },
  [401],
);
await call(a, '/users/' + b.public_id);
const conv = (await call(a, '/conversations', 'POST', { target_user_public_id: b.public_id })).data;
await call(a, '/conversations?limit=1');
const page = (await call(a, `/conversations/${conv}/messages?limit=1`)).data;
if (page?.next_cursor)
  await call(a, `/conversations/${conv}/messages?limit=2&before_message_id=${page.next_cursor}`);
await call(
  a,
  `/conversations/${conv}/messages`,
  'POST',
  { content: '', media_type: 'TEXT' },
  [400],
);
const f = new FormData();
f.append(
  'file',
  new Blob(['AI Social frontend integration file\n'], { type: 'text/plain' }),
  'frontend-integration.txt',
);
f.append('conversation_public_id', conv);
const file = (await call(a, '/files', 'POST', f)).data;
if (file?.public_id) {
  await call(a, '/files/' + file.public_id);
  await call(a, '/files/metadata/batch', 'POST', { file_public_ids: [file.public_id] });
  await call(a, `/conversations/${conv}/messages`, 'POST', {
    content: '联调附件',
    media_type: 'FILE',
    file_public_id: file.public_id,
  });
  await call(b, `/files/${file.public_id}/content`);
  await call(c, `/files/${file.public_id}`, 'GET', undefined, [403, 404]);
}
const sse = new AbortController();
try {
  const r = await fetch(base + '/sse/subscribe', {
    headers: { Authorization: 'Bearer ' + a.access_token, Accept: 'text/event-stream' },
    signal: sse.signal,
  });
  const chunk = await Promise.race([
    r.body.getReader().read(),
    new Promise((_, reject) => setTimeout(() => reject(new Error('首帧超时')), 5000)),
  ]);
  const text = new TextDecoder().decode(chunk.value);
  records.push({
    method: 'GET',
    path: '/sse/subscribe',
    http_status: r.status,
    pass: r.ok && text.includes('CONNECTION_ESTABLISHED'),
  });
  console.log('SSE', r.status, text.includes('CONNECTION_ESTABLISHED') ? 'PASS' : 'FAIL');
} catch (e) {
  records.push({ method: 'GET', path: '/sse/subscribe', pass: false, error: e.message });
} finally {
  sse.abort();
}
const group = (
  await call(a, '/groups', 'POST', {
    name: '前端权限回归临时群',
    member_public_ids: [b.public_id, c.public_id],
  })
).data;
if (group?.public_id) {
  const p = '/groups/' + group.public_id;
  await call(a, p + '/members?keyword=联调');
  await call(b, p + '/members/admins', 'POST', { user_public_ids: [c.public_id] }, [403]);
  await call(a, p + '/members/admins', 'POST', { user_public_ids: [b.public_id] });
  await call(b, p + '/members/' + c.public_id, 'DELETE');
  await call(a, p + '/members', 'POST', { user_public_ids: [c.public_id] });
  await call(a, p + '/members/admins', 'DELETE', { user_public_ids: [b.public_id] });
  await call(a, p + '/members/owner', 'POST', { new_owner_public_id: b.public_id });
  await call(a, p + '/members/me', 'DELETE');
  await call(b, p);
  await call(b, p + '/members/owner', 'DELETE');
}
await call(a, '/friends/' + c.public_id, 'DELETE');
await call(a, '/friends/requests', 'POST', {
  target_user_public_id: c.public_id,
  message: '前端撤销申请回归',
});
let requests = (await call(a, '/friends/requests')).data;
let outgoing = requests?.outgoing?.find((r) => r.status === 'PENDING');
if (outgoing) await call(a, '/friends/requests/' + outgoing.request_public_id, 'DELETE');
await call(a, '/friends/requests', 'POST', {
  target_user_public_id: c.public_id,
  message: '前端接受申请回归',
});
requests = (await call(c, '/friends/requests')).data;
let incoming = requests?.incoming?.find(
  (r) => r.sender?.public_id === a.public_id && r.status === 'PENDING',
);
if (incoming)
  await call(c, '/friends/requests/' + incoming.request_public_id + '/accept', 'POST', {});
const models = (await call(a, '/ai/models')).data?.models || [];
const option = models[0]?.option_code;
if (option) {
  for (const [path, body] of [
    ['polish', { message: '你好，明天见。' }],
    ['translate', { text: '你好', target_language: 'en' }],
    ['smart-reply', { message: '明天参加会议吗？', conversation_public_id: conv }],
    [
      'summarize',
      {
        content: '会议决定明天下午两点讨论产品方案。',
        summary_type: 'chat',
        target_length: 'short',
      },
    ],
  ])
    await call(a, '/ai/interactions/' + path, 'POST', { ...body, model_option_code: option });
  await call(a, '/ai/interactions/schedule', 'POST', {
    messages: [
      {
        sender: '我',
        content: '明天下午两点在线上开产品讨论会',
        timestamp: new Date().toISOString(),
      },
    ],
    model_option_code: option,
  });
}
await call(a, '/ai/jobs?task_type=CHAT_SUMMARY&limit=5');
const jobs = (await call(a, '/ai/jobs?limit=5')).data;
if (jobs?.items?.[0]) {
  await call(a, '/ai/jobs/' + jobs.items[0].public_id);
  await call(b, '/ai/jobs/' + jobs.items[0].public_id, 'GET', undefined, [403, 404]);
}
await call(a, '/ai/profiles');
await call(a, '/ai/usage');
await call(a, '/users/me/ai-persona', 'DELETE');
await call(
  a,
  '/users/me/password',
  'POST',
  { current_password: 'WrongPassword1!', new_password: 'ValidReplacement1!' },
  [400],
);
const refresh = (await call(a, '/auth/refresh', 'POST', { refresh_token: a.refresh_token })).data;
if (refresh?.access_token) {
  Object.assign(a, refresh);
  await call(a, '/users/me');
}
await writeFile(
  new URL('../.local/session.json', import.meta.url),
  JSON.stringify(accounts, null, 2),
);
let redis;
const config = await readFile(
  new URL('../../ai-social-app/src/main/resources/application.yaml', import.meta.url),
  'utf8',
);
const host = config.match(/redis:\s*\n\s*host:\s*(\S+)/)[1],
  port = Number(config.match(/redis:[\s\S]*?port:\s*(\d+)/)[1]),
  password = config.match(/redis:[\s\S]*?password:\s*(\S+)/)[1];
redis = await new Promise((resolve) => {
  const socket = net.createConnection({ host, port });
  let output = '';
  const timer = setTimeout(() => {
    socket.destroy();
    resolve({ pass: false, error: '连接超时' });
  }, 5000);
  socket.on('connect', () =>
    socket.write(
      `*2\r\n$4\r\nAUTH\r\n$${Buffer.byteLength(password)}\r\n${password}\r\n*1\r\n$4\r\nPING\r\n`,
    ),
  );
  socket.on('data', (buf) => {
    output += buf.toString();
    if (output.includes('+PONG')) {
      clearTimeout(timer);
      socket.end();
      resolve({ pass: true, operation: 'AUTH + PING only' });
    }
  });
  socket.on('error', (e) => {
    clearTimeout(timer);
    resolve({ pass: false, error: e.message });
  });
});
const report = {
  timestamp: new Date().toISOString(),
  records,
  redis,
  summary: {
    total: records.length,
    passed: records.filter((r) => r.pass).length,
    failed: records.filter((r) => !r.pass).length,
  },
};
await mkdir(new URL('../artifacts/integration/', import.meta.url), { recursive: true });
await writeFile(
  new URL('../artifacts/integration/frontend-contract-regression.json', import.meta.url),
  JSON.stringify(report, null, 2),
);
console.log('Summary', report.summary, 'Redis', redis);
