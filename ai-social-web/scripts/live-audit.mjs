import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import pg from 'pg';
const root = new URL('../../', import.meta.url);
const config = await readFile(
  new URL('ai-social-app/src/main/resources/application.yaml', root),
  'utf8',
);
const port = config.match(/port:\s*(\d+)/)[1];
const base = process.env.AUDIT_API_BASE || `http://127.0.0.1:${port}/api/v1`;
const records = [];
async function call(actor, path, method = 'GET', body, expected = [200, 201, 202, 204]) {
  const headers = {};
  if (actor?.access_token) headers.Authorization = `Bearer ${actor.access_token}`;
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const started = Date.now();
  let response, data;
  try {
    response = await fetch(base + path, {
      method,
      headers,
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(60000),
    });
    const text = await response.text();
    try {
      data = JSON.parse(text);
    } catch {
      data = text.slice(0, 1500);
    }
  } catch (e) {
    records.push({ path, method, error: e.message, pass: false });
    return null;
  }
  const row = {
    method,
    path,
    status: response.status,
    elapsed_ms: Date.now() - started,
    pass: expected.includes(response.status) && !(data?.code >= 400),
  };
  if (!row.pass) row.response = data;
  records.push(row);
  console.log(method, path, response.status, row.pass ? 'PASS' : 'FAIL');
  return data?.data ?? data;
}
await mkdir(new URL('.local/', new URL('../', import.meta.url)), { recursive: true });
let accounts;
try {
  accounts = JSON.parse(
    await readFile(new URL('../.local/accounts.json', import.meta.url), 'utf8'),
  );
} catch {
  const suffix = Date.now().toString(36);
  accounts = ['a', 'b', 'c'].map((letter) => ({
    username: `webqa_${suffix}_${letter}`,
    password: 'Aa9!' + randomBytes(18).toString('hex'),
    email: `webqa_${suffix}_${letter}@example.test`,
  }));
  await writeFile(
    new URL('../.local/accounts.json', import.meta.url),
    JSON.stringify(accounts, null, 2),
  );
  for (const a of accounts) await call(null, '/auth/register', 'POST', a);
}
for (const a of accounts) {
  if (!/^[A-Za-z0-9@$!%*?&]+$/.test(a.password) || !a.password.startsWith('Aa9!')) {
    a.password = 'Aa9!' + randomBytes(18).toString('hex');
    await call(null, '/auth/register', 'POST', a);
  }
  Object.assign(
    a,
    await call(null, '/auth/login', 'POST', { username: a.username, password: a.password }),
  );
  const me = await call(a, '/users/me');
  a.public_id = me?.public_id;
}
await writeFile(
  new URL('../.local/accounts.json', import.meta.url),
  JSON.stringify(
    accounts.map(({ username, password, email }) => ({ username, password, email })),
    null,
    2,
  ),
);
const [a, b, c] = accounts;
if (!a.public_id) throw new Error('测试账号登录失败，已停止写入');
await call(a, '/users/me', 'PATCH', { nickname: '联调 · 林沐', ai_analysis_enabled: false });
await call(b, '/users/me', 'PATCH', { nickname: '联调 · 陈一诺', ai_analysis_enabled: false });
await call(c, '/users/me', 'PATCH', { nickname: '联调 · 许知远', ai_analysis_enabled: false });
for (const other of [b, c]) {
  const current = await call(a, '/friends');
  if (!current?.some?.((f) => f.public_id === other.public_id)) {
    await call(a, '/friends/requests', 'POST', {
      target_user_public_id: other.public_id,
      message: '前端联调测试好友申请',
    });
    const req = await call(other, '/friends/requests');
    const incoming = req?.incoming?.find(
      (r) => r.sender?.public_id === a.public_id && r.status === 'PENDING',
    );
    if (incoming)
      await call(other, `/friends/requests/${incoming.request_public_id}/accept`, 'POST', {});
  }
}
const conv = await call(a, '/conversations', 'POST', { target_user_public_id: b.public_id });
if (typeof conv === 'string') {
  await call(a, `/conversations/${conv}/messages`, 'POST', {
    content: '前端联调消息：明天下午两点讨论方案',
    media_type: 'TEXT',
    temp_id: randomBytes(8).toString('hex'),
  });
  await call(b, `/conversations/${conv}/messages`);
  await call(a, `/conversations/${conv}/messages?limit=1`);
}
const group = await call(a, '/groups', 'POST', {
  name: `共鸣联调小组 ${new Date().toISOString().slice(0, 10)}`,
  member_public_ids: [b.public_id],
});
if (group?.public_id) {
  const id = group.public_id;
  await call(a, '/groups');
  await call(a, `/groups/${id}`);
  await call(a, `/groups/${id}`, 'PATCH', { name: '共鸣 · 灵感协作小组' });
  await call(a, `/groups/${id}/members`, 'POST', { user_public_ids: [c.public_id] });
  await call(a, `/groups/${id}/members`);
  await call(a, `/groups/${id}/members/admins`, 'POST', { user_public_ids: [b.public_id] });
  await call(a, `/groups/${id}/members/admins`);
  await call(a, `/groups/${id}/members/admins`, 'DELETE', { user_public_ids: [b.public_id] });
  await call(b, `/groups/${id}`, 'PATCH', { name: '不应允许普通成员改名' }, [403]);
  await call(a, `/conversations/${id}/messages`, 'POST', {
    content: '欢迎来到共鸣联调小组',
    media_type: 'TEXT',
  });
  accounts[0].group_id = id;
}
const start = new Date();
start.setDate(start.getDate() + 1);
start.setHours(14, 0, 0, 0);
const end = new Date(start.getTime() + 3600000);
const schedule = await call(a, '/schedules', 'POST', {
  title: '前端联调验证日程',
  start_time: start.toISOString(),
  end_time: end.toISOString(),
  location: '共鸣线上空间',
  description: '仅测试账号数据',
});
if (schedule?.public_id) {
  await call(a, `/schedules/${schedule.public_id}`, 'PUT', {
    title: '已更新 · 联调验证',
    start_time: start.toISOString(),
    end_time: end.toISOString(),
    location: '线上',
  });
  await call(a, `/schedules/${schedule.public_id}`, 'DELETE');
}
await call(
  a,
  `/schedules?start_time=${encodeURIComponent(new Date(Date.now() - 86400000).toISOString())}&end_time=${encodeURIComponent(end.toISOString())}`,
);
await call(a, '/conversations');
await call(a, '/friends?keyword=联调');
await call(a, '/ai/models');
await call(a, '/ai/jobs');
await call(a, '/ai/profiles');
await call(a, '/ai/usage');
await call(a, '/ai/jobs/message-candidates');
await writeFile(
  new URL('../.local/session.json', import.meta.url),
  JSON.stringify(accounts, null, 2),
);
let database;
const jdbc = config.match(/url:\s*jdbc:postgresql:\/\/([^\s]+)/)[1],
  username = config.match(/username:\s*(\S+)/)[1],
  password = config.match(/password:\s*(\S+)/)[1];
const client = new pg.Client({
  host: jdbc.split('/')[0].split(':')[0],
  database: jdbc.split('/')[1],
  user: username,
  password,
  connectionTimeoutMillis: 10000,
});
try {
  await client.connect();
  await client.query('BEGIN READ ONLY');
  const tables = await client.query(
    "SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name",
  );
  const counts = await client.query(
    "SELECT (SELECT count(*) FROM users) users,(SELECT count(*) FROM conversations WHERE type='GROUP') groups,(SELECT count(*) FROM messages) messages",
  );
  database = { tables: tables.rows.map((r) => r.table_name), counts: counts.rows[0] };
  await client.query('ROLLBACK');
} catch (e) {
  database = { error: e.message };
} finally {
  await client.end().catch(() => {});
}
const report = {
  timestamp: new Date().toISOString(),
  base,
  records,
  database,
  summary: {
    total: records.length,
    passed: records.filter((r) => r.pass).length,
    failed: records.filter((r) => !r.pass).length,
  },
};
await mkdir(new URL('../artifacts/integration/', import.meta.url), { recursive: true });
await writeFile(new URL('../artifacts/integration/frontend-live-evidence.json', import.meta.url), JSON.stringify(report, null, 2));
console.log('Summary', report.summary, 'Database', database);
