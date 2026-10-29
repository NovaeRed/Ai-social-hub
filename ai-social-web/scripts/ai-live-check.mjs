import { readFile, writeFile, mkdir } from 'node:fs/promises';
const a = JSON.parse(
    await readFile(new URL('../.local/accounts.json', import.meta.url), 'utf8'),
  )[0],
  base = process.env.AUDIT_API_BASE || 'http://127.0.0.1:8082/api/v1';
const auth = await (
  await fetch(base + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: a.username, password: a.password }),
  })
).json();
const token = auth.data?.access_token;
if (!token) throw new Error('测试账号登录失败');
const headers = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
  catalog = await (await fetch(base + '/ai/models', { headers })).json(),
  models = catalog.data.models;
const tests = [
  ['polish', 'POLISH', { message: '你好，明天见。' }],
  [
    'smart-reply',
    'SMART_REPLY',
    {
      message: '明天参加会议吗？',
      conversation_history: [
        { sender: '朋友', content: '我们正在安排会议', timestamp: new Date().toISOString() },
      ],
    },
  ],
  ['translate', 'TRANSLATION', { text: '你好', target_language: 'en' }],
  [
    'summarize',
    'CHAT_SUMMARY',
    { content: '会议决定明天下午两点讨论产品方案。', summary_type: 'chat', target_length: 'short' },
  ],
  [
    'schedule',
    'SCHEDULE_EXTRACTION',
    {
      messages: [
        {
          sender: '我',
          content: '明天下午两点在线上开产品讨论会',
          timestamp: new Date().toISOString(),
        },
      ],
    },
  ],
];
const records = [];
for (const [path, type, body] of tests) {
  const supported = models.filter((m) => m.capabilities.includes(type)),
    selected = supported.find((m) => m.name === 'deepseek-v4.1-flash' && m.provider === 'dashscope');
  if (!selected) {
    records.push({ path, pass: false, error: 'deepseek-v4.1-flash 未配置或不支持该能力' });
    continue;
  }
  console.log('开始验证', path, selected.option_code);
  const start = Date.now();
  try {
    const r = await fetch(base + '/ai/interactions/' + path, {
        method: 'POST',
        headers,
        body: JSON.stringify({ ...body, model_option_code: selected.option_code }),
        signal: AbortSignal.timeout(45000),
      }),
      raw = await r.text();
    let data;
    try {
      data = JSON.parse(raw);
    } catch {
      data = null;
    }
    const pass =
      r.ok &&
      !(data?.code >= 400) &&
      ((r.headers.get('content-type') || '').includes('text/event-stream')
        ? raw.includes('output')
        : !!data?.data?.schedules);
    records.push({
      path: '/ai/interactions/' + path,
      model: selected.option_code,
      http_status: r.status,
      elapsed_ms: Date.now() - start,
      pass,
      response: data || raw.slice(0, 1800),
    });
    console.log(path, pass ? 'PASS' : 'FAIL', Date.now() - start);
  } catch (e) {
    records.push({
      path,
      model: selected.option_code,
      pass: false,
      error: e.message,
      elapsed_ms: Date.now() - start,
    });
  }
}
await mkdir(new URL('../artifacts/integration/', import.meta.url), { recursive: true });
await writeFile(
  new URL('../artifacts/integration/frontend-ai-live-evidence.json', import.meta.url),
  JSON.stringify({ timestamp: new Date().toISOString(), models, records }, null, 2),
);
console.log('已保存 AI 联调证据');
