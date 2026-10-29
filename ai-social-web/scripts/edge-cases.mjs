import { readFile, writeFile, mkdir } from 'node:fs/promises';
const accounts = JSON.parse(
    await readFile(new URL('../.local/accounts.json', import.meta.url), 'utf8'),
  ),
  base = 'http://127.0.0.1:8082/api/v1',
  records = [];
async function call(actor, path, method = 'GET', body, expected = [200, 201, 202, 204]) {
  const headers = { 'Content-Type': 'application/json' };
  if (actor?.access_token) headers.Authorization = 'Bearer ' + actor.access_token;
  const r = await fetch(base + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(20000),
  });
  const raw = await r.text();
  let json;
  try {
    json = JSON.parse(raw);
  } catch {
    json = null;
  }
  const status = json?.code || r.status;
  const data = json?.data ?? json;
  records.push({
    method,
    path,
    http_status: r.status,
    business_status: status,
    pass: expected.includes(status),
    ...(path.startsWith('/auth/') ? {} : { response: data }),
  });
  console.log(method, path, r.status, status);
  return data;
}
for (const a of accounts) {
  Object.assign(
    a,
    await call(null, '/auth/login', 'POST', { username: a.username, password: a.password }),
  );
  a.public_id = (await call(a, '/users/me')).public_id;
}
const [a, b, c] = accounts;
const g = await call(a, '/groups', 'POST', {
  name: '契约边界回归空群',
  member_public_ids: [b.public_id],
});
const p = '/groups/' + g.public_id;
await call(a, p);
await call(c, p, 'GET', undefined, [403, 404]);
await call(c, p + '/members', 'GET', undefined, [403, 404]);
await call(c, p + '/members/admins', 'GET', undefined, [403, 404]);
await call(a, p + '/members/admins', 'DELETE', { user_public_ids: [] }, [400]);
await call(a, p + '/members/admins', 'DELETE', { user_public_ids: [null] }, [400]);
await call(a, '/conversations/' + g.public_id + '/messages', 'POST', {
  content: '用于验证含消息群聊的解散行为',
  media_type: 'TEXT',
});
await call(a, '/conversations/' + g.public_id + '/messages');
await call(a, p + '/members/owner', 'DELETE');
const empty = await call(a, '/groups', 'POST', { name: '空群正常解散验证', member_public_ids: [] });
await call(a, '/groups/' + empty.public_id + '/members/owner', 'DELETE');
await call(b, '/friends/requests', 'POST', {
  target_user_public_id: c.public_id,
  message: '取消申请回归',
});
let pending = (await call(b, '/friends/requests')).outgoing.find((r) => r.status === 'PENDING');
if (pending) await call(b, '/friends/requests/' + pending.request_public_id, 'DELETE');
await call(b, '/friends/requests', 'POST', {
  target_user_public_id: c.public_id,
  message: '拒绝申请回归',
});
pending = (await call(c, '/friends/requests')).incoming.find(
  (r) => r.sender.public_id === b.public_id && r.status === 'PENDING',
);
if (pending) await call(c, '/friends/requests/' + pending.request_public_id, 'DELETE');
await call(b, '/friends/requests', 'POST', {
  target_user_public_id: c.public_id,
  message: '接受申请回归',
});
pending = (await call(c, '/friends/requests')).incoming.find(
  (r) => r.sender.public_id === b.public_id && r.status === 'PENDING',
);
if (pending)
  await call(c, '/friends/requests/' + pending.request_public_id + '/accept', 'POST', {});
await call(c, '/friends/requests', 'POST', {
  target_user_public_id: a.public_id,
  message: '恢复联调好友关系',
});
pending = (await call(a, '/friends/requests')).incoming.find(
  (r) => r.sender.public_id === c.public_id && r.status === 'PENDING',
);
if (pending)
  await call(a, '/friends/requests/' + pending.request_public_id + '/accept', 'POST', {});
const report = {
  timestamp: new Date().toISOString(),
  records,
  summary: {
    total: records.length,
    passed: records.filter((r) => r.pass).length,
    failed: records.filter((r) => !r.pass).length,
  },
};
await mkdir(new URL('../artifacts/integration/', import.meta.url), { recursive: true });
await writeFile(
  new URL('../artifacts/integration/frontend-edge-evidence.json', import.meta.url),
  JSON.stringify(report, null, 2),
);
console.log(report.summary);
