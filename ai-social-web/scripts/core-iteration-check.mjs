import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
const base = process.env.AUDIT_API_BASE || 'http://127.0.0.1:8083/api/v1';
const accounts = JSON.parse(
  await readFile(new URL('../.local/accounts.json', import.meta.url), 'utf8'),
);
const checks = [];
function check(name, pass, status) {
  checks.push({ name, pass: !!pass, ...(status ? { status } : {}) });
  console.log(pass ? 'PASS' : 'FAIL', name);
}
async function call(actor, path, method = 'GET', body) {
  const headers = actor ? { Authorization: 'Bearer ' + actor.token } : {};
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + path, {
    method,
    headers,
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(20000),
  });
  const result = await response.json().catch(() => ({}));
  return { status: result.code || response.status, data: result.data, error: result.error_code };
}
const actors = [];
for (const a of accounts.slice(0, 3)) {
  const login = await call(null, '/auth/login', 'POST', {
    username: a.username,
    password: a.password,
  });
  if (!login.data?.access_token) throw new Error('联调账号登录失败');
  const actor = { token: login.data.access_token, username: a.username };
  actor.me = (await call(actor, '/users/me')).data;
  actors.push(actor);
}
const [a, b, c] = actors;
const strangerName = 'iterqa_' + randomBytes(5).toString('hex');
const strangerPassword = 'Gg1@' + randomBytes(12).toString('hex');
const registration = await call(null, '/auth/register', 'POST', {
  username: strangerName,
  password: strangerPassword,
  email: strangerName + '@example.test',
});
const strangerLogin = registration.data?.access_token
  ? registration
  : await call(null, '/auth/login', 'POST', { username: strangerName, password: strangerPassword });
if (!strangerLogin.data?.access_token) throw new Error('陌生人联调账号创建失败');
await writeFile(
  new URL('../.local/iteration-account.json', import.meta.url),
  JSON.stringify({ username: strangerName, password: strangerPassword }),
);
const stranger = { token: strangerLogin.data.access_token };
const phone = '139' + String(randomBytes(4).readUInt32BE() % 100000000).padStart(8, '0');
const strangerProfile = await call(stranger, '/users/me', 'PATCH', {
  nickname: '联调 · 陌生人',
  phone,
});
const strangerId = strangerProfile.data?.public_id;
const phoneFound = await call(
  a,
  '/users/lookup?' + new URLSearchParams({ type: 'PHONE', value: phone }),
);
check(
  'phone lookup locates the exact test account without returning contacts',
  phoneFound.data?.public_id === strangerId && !('phone' in (phoneFound.data || {})),
);
for (let attempt = 0; attempt < 2; attempt++) {
  const application = await call(a, '/friends/requests', 'POST', {
    target_user_public_id: strangerId,
    message: '迭代验收好友申请',
  });
  check(
    attempt
      ? 'removed friend can receive a new application'
      : 'stranger friend application succeeds',
    [200, 201, 204].includes(application.status),
    application.status,
  );
  const requests = await call(stranger, '/friends/requests');
  const incoming = requests.data?.incoming.find(
    (request) => request.sender?.public_id === a.me.public_id && request.status === 'PENDING',
  );
  check('recipient sees a pending application', !!incoming);
  if (!incoming) break;
  const accepted = await call(
    stranger,
    '/friends/requests/' + incoming.request_public_id + '/accept',
    'POST',
    {},
  );
  check('friend application is accepted', [200, 201, 204].includes(accepted.status));
  const friends = await call(a, '/friends');
  check(
    'friendship is visible to the sender',
    friends.data?.some((person) => person.public_id === strangerId),
  );
  const removed = await call(a, '/friends/' + strangerId, 'DELETE');
  check('friendship can be removed', [200, 204].includes(removed.status));
}
const found = await call(
  a,
  '/users/lookup?' + new URLSearchParams({ type: 'USERNAME', value: b.username }),
);
check(
  'exact username lookup confirms the correct person',
  found.data?.public_id === b.me.public_id,
);
check(
  'lookup returns only public confirmation fields',
  found.data &&
    Object.keys(found.data).every((k) =>
      ['public_id', 'username', 'nickname', 'avatar_url'].includes(k),
    ),
);
const invalidPhone = await call(a, '/users/lookup?type=PHONE&value=123');
check('invalid phone returns a parameter error', invalidPhone.status === 400, invalidPhone.status);
const modelResult = await call(a, '/ai/models');
check(
  'AI model catalog is a successful explicit list',
  modelResult.status === 200 && Array.isArray(modelResult.data?.models),
);
const profiles = await call(a, '/ai/profiles');
check(
  'profile query works after the authorized schema migration',
  profiles.status === 200 && Array.isArray(profiles.data?.items),
);
const conversations = await call(a, '/conversations');
check(
  'private conversations have peer names',
  conversations.data?.items
    .filter((item) => item.type === 'PRIVATE')
    .every((item) => item.name && !item.name.startsWith('AIHub_')),
);
const group = await call(a, '/groups', 'POST', {
  name: '迭代验收 ' + Date.now(),
  member_public_ids: [b.me.public_id],
});
if (!group.data?.public_id) throw new Error('联调建群失败');
const id = group.data.public_id;
try {
  const detail = await call(a, '/groups/' + id);
  check(
    'group count includes the owner',
    detail.data?.member_count === 2 && detail.data?.members.length === 2,
  );
  for (const suffix of ['', '/members', '/members/admins']) {
    const denied = await call(c, '/groups/' + id + suffix);
    check(
      'outsider cannot read group ' + (suffix || 'detail'),
      [403, 404].includes(denied.status),
      denied.status,
    );
  }
  const sent = await call(a, '/conversations/' + id + '/messages', 'POST', {
    content: '真实发送者验收',
    media_type: 'TEXT',
    temp_id: crypto.randomUUID(),
  });
  check(
    'send response includes the real sender',
    sent.data?.sender?.public_id === a.me.public_id && sent.data.sender.nickname === a.me.nickname,
  );
  const peer = await call(b, '/conversations/' + id + '/messages', 'POST', {
    content: '另一位成员回复',
    media_type: 'TEXT',
    temp_id: crypto.randomUUID(),
  });
  const history = await call(a, '/conversations/' + id + '/messages');
  check(
    'history preserves both sender identities',
    history.data?.items.some(
      (m) => m.public_id === sent.data?.public_id && m.sender?.public_id === a.me.public_id,
    ) &&
      history.data?.items.some(
        (m) => m.public_id === peer.data?.public_id && m.sender?.public_id === b.me.public_id,
      ),
  );
  const form = new FormData();
  form.append(
    'file',
    new Blob(['iteration attachment check'], { type: 'text/plain' }),
    'iteration-check.txt',
  );
  form.append('conversation_public_id', id);
  const uploaded = await call(a, '/files', 'POST', form);
  check('attachment upload succeeds', !!uploaded.data?.public_id);
  if (uploaded.data?.public_id) {
    const fileMessage = await call(a, '/conversations/' + id + '/messages', 'POST', {
      media_type: 'FILE',
      file_public_id: uploaded.data.public_id,
      temp_id: crypto.randomUUID(),
    });
    check(
      'file message includes sender and filename',
      fileMessage.data?.sender?.public_id === a.me.public_id &&
        fileMessage.data.file?.original_filename === 'iteration-check.txt',
    );
  }
} finally {
  const removed = await call(a, '/groups/' + id + '/members/owner', 'DELETE');
  check(
    'group with messages dissolves successfully',
    [200, 204].includes(removed.status),
    removed.status,
  );
  const denied = await call(a, '/conversations/' + id + '/messages', 'POST', {
    content: '解散后不得写入',
    media_type: 'TEXT',
  });
  check('dissolved group rejects new messages', [403, 404].includes(denied.status), denied.status);
}
const start = new Date(Date.now() + 86400000).toISOString(),
  end = new Date(Date.now() + 90000000).toISOString();
const schedule = await call(a, '/schedules', 'POST', {
  title: '迭代日程验收',
  start_time: start,
  end_time: end,
  location: '线上',
});
check('schedule creation succeeds', !!schedule.data?.public_id);
if (schedule.data?.public_id) {
  const saved = await call(a, '/schedules/' + schedule.data.public_id, 'PUT', {
    title: '迭代日程已更新',
    start_time: start,
    end_time: end,
    location: '线上',
  });
  check('schedule update succeeds', saved.data?.title === '迭代日程已更新');
  const removed = await call(a, '/schedules/' + schedule.data.public_id, 'DELETE');
  check('schedule deletion succeeds', [200, 204].includes(removed.status));
}
const jobs = await call(a, '/ai/jobs');
if (jobs.data?.items[0]) {
  const job = jobs.data.items[0];
  const detail = await call(a, '/ai/jobs/' + job.public_id);
  const denied = await call(c, '/ai/jobs/' + job.public_id);
  check(
    'owner can read task detail',
    detail.status === 200 && detail.data?.public_id === job.public_id,
  );
  check('other accounts cannot read task detail', denied.status === 404, denied.status);
}
await mkdir(new URL('../artifacts/integration/', import.meta.url), { recursive: true });
await writeFile(
  new URL('../artifacts/integration/core-iteration-evidence.json', import.meta.url),
  JSON.stringify(
    {
      timestamp: new Date().toISOString(),
      base,
      checks,
      aiModels: modelResult.data?.models?.length ?? null,
      summary: { passed: checks.filter((c) => c.pass).length, total: checks.length },
    },
    null,
    2,
  ),
);
if (checks.some((c) => !c.pass)) process.exitCode = 1;
