import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { randomBytes } from 'node:crypto';
const fixture = fileURLToPath(new URL('../.local/accounts.json', import.meta.url));
let accounts: any[] = [];
try {
  accounts = JSON.parse(readFileSync(fixture, 'utf8'));
} catch {}
test.skip(!accounts.length, '先执行 scripts/live-audit.mjs 创建独立测试账号');
test('registration persists the chosen nickname and settings do not expose the account ID', async ({
  page,
}) => {
  const username = 'webqa_name_' + Date.now().toString(36),
    password = 'Qa1!' + randomBytes(12).toString('hex'),
    nickname = '初夏';
  await page.goto('/#/login');
  await page.getByRole('button', { name: '还没有账号？立即注册', exact: true }).click();
  await page.getByLabel('用户名', { exact: true }).fill(username);
  await page.getByLabel('昵称（选填）', { exact: true }).fill(nickname);
  await page.getByLabel('邮箱', { exact: true }).fill(username + '@example.test');
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '注册并登录', exact: true }).click();
  await expect(page.locator('.page-heading h1')).toContainText('你好，' + nickname);
  await page.reload();
  await expect(page.locator('.page-heading h1')).toContainText('你好，' + nickname);
  await page.goto('/#/settings');
  await expect(page.getByLabel('昵称', { exact: true })).toHaveValue(nickname);
  const saved = await page.evaluate(async () => {
    const r = await fetch('/api/v1/users/me', {
      headers: { Authorization: 'Bearer ' + sessionStorage.getItem('access_token') },
    });
    return (await r.json()).data;
  });
  expect(saved.nickname).toBe(nickname);
  await expect(page.locator('main')).not.toContainText(saved.public_id);
  await expect(page.locator('main')).not.toContainText('公开 ID');
});
async function login(page: Page, index = 0) {
  await page.goto('/#/login');
  await page.getByLabel('用户名', { exact: true }).fill(accounts[index].username);
  await page.getByLabel('密码', { exact: true }).fill(accounts[index].password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.page-heading h1')).toContainText('你好');
  await expect(page.locator('.loading')).toHaveCount(0);
  await expect(page.locator('.error')).toHaveCount(0);
}

test('file selection previews before upload and retains the file for a safe retry', async ({
  page,
}) => {
  await login(page);
  await page.setViewportSize({ width: 580, height: 892 });
  await page.goto('/#/messages');
  await page.locator('.chat-list .conversation-row').first().click();
  const preview = page.getByRole('region', { name: '待发送文件' });
  let uploads = 0,
    sends = 0;
  const messageKeys: string[] = [];
  await page.route('**/files', async (route) => {
    uploads++;
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: { public_id: 'preview-file' } }),
    });
  });
  await page.route('**/conversations/*/messages', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    sends++;
    messageKeys.push(route.request().postDataJSON().temp_id);
    expect(route.request().postDataJSON().file_public_id).toBe('preview-file');
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(
        sends === 1
          ? { code: 400, msg: '发送暂时失败' }
          : {
              code: 200,
              data: {
                public_id: 'preview-message',
                media_type: 'FILE',
                created_at: new Date().toISOString(),
                file: {
                  public_id: 'preview-file',
                  original_filename: '确认发送.pdf',
                  size_bytes: 2048,
                },
              },
            },
      ),
    });
  });
  const input = page.getByLabel('上传聊天文件');
  await expect(input).toBeEnabled();
  await input.setInputFiles({
    name: '选错的文件.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.alloc(1024),
  });
  await expect(preview).toContainText('选错的文件.pdf');
  await expect(preview).toContainText('1.0 KB');
  expect(uploads).toBe(0);
  await page.getByRole('button', { name: '移除待发送文件' }).click();
  await expect(preview).toHaveCount(0);
  expect(uploads).toBe(0);
  await input.setInputFiles({
    name: '确认发送.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.alloc(2048),
  });
  await expect(preview).toContainText('2.0 KB');
  await page.screenshot({ path: 'artifacts/file-confirm-preview.png', fullPage: true });
  expect(uploads).toBe(0);
  await page.getByRole('button', { name: '发送文件', exact: true }).click();
  await expect(page.locator('.composer').getByRole('alert')).toContainText('发送暂时失败');
  await expect(preview).toContainText('确认发送.pdf');
  await page.getByRole('button', { name: '发送文件', exact: true }).click();
  await expect(preview).toHaveCount(0);
  expect(uploads).toBe(1);
  expect(sends).toBe(2);
  expect(messageKeys[0]).toBeTruthy();
  expect(messageKeys[1]).toBe(messageKeys[0]);
  await expect(page.locator('.file-message').last()).toContainText('确认发送.pdf');
});

test('month changes keep the calendar in place and ignore responses for older months', async ({
  page,
}) => {
  await login(page);
  await page.goto('/#/schedules');
  const initialMonth = await page.locator('.calendar h3').innerText();
  const bounds = await page.locator('.calendar').boundingBox();
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => (release = resolve));
  let calls = 0;
  let oldUrl = '';
  const selectedDate = await page.locator('.day-agenda .section-heading h3').innerText();
  const date = selectedDate.slice(0, 10);
  await page.route('**/schedules?*', async (route) => {
    const call = ++calls;
    if (call === 1) {
      oldUrl = route.request().url();
      await gate;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data:
          call === 1
            ? [
                {
                  public_id: 'outdated-schedule',
                  title: '过期月份结果不得显示',
                  start_time: date + 'T10:00:00',
                  end_time: date + 'T11:00:00',
                },
              ]
            : [],
      }),
    });
  });
  await page.getByRole('button', { name: '下个月', exact: true }).click();
  await expect(page.locator('.calendar [role=status]')).toContainText('正在更新');
  expect((await page.locator('.calendar').boundingBox())!.y).toBe(bounds!.y);
  await expect(page.locator('.loading')).toHaveCount(0);
  await page.getByRole('button', { name: '上个月', exact: true }).click();
  await expect(page.locator('.calendar h3')).toHaveText(initialMonth);
  await expect(page.locator('.calendar [role=status]')).not.toContainText('正在更新');
  const oldResponse = page.waitForResponse((response) => response.url() === oldUrl);
  release();
  await (await oldResponse).finished();
  await page.evaluate(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
  );
  await expect(page.locator('.day-agenda')).not.toContainText('过期月份结果不得显示');
  await expect(page.locator('.calendar h3')).toHaveText(initialMonth);
  expect((await page.locator('.calendar').boundingBox())!.y).toBe(bounds!.y);
});

test('contacts and AI details use names while keeping identifiers inside API calls', async ({
  page,
}) => {
  await login(page);
  const hiddenId = '01M5KHE3Y3H8WT63RSJYE63CFJ';
  const fulfill = (data: unknown) => ({
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, data }),
  });
  await page.route('**/groups?*', (route) =>
    route.fulfill(
      fulfill({
        items: [{ public_id: hiddenId, name: '联系人测试群' }],
        has_more: false,
      }),
    ),
  );
  await page.route('**/groups/*/members', (route) =>
    route.fulfill(fulfill([{ public_id: hiddenId, nickname: '夏晚', username: 'xiawan' }])),
  );
  let target = '';
  await page.route('**/friends/requests', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    target = route.request().postDataJSON().target_user_public_id;
    await route.fulfill(fulfill({ request_public_id: hiddenId }));
  });
  await page.goto('/#/friends');
  await page.getByRole('button', { name: '添加好友', exact: true }).click();
  await page.getByLabel('搜索联系人').fill('夏晚');
  await page.locator('.contact-option').filter({ hasText: '夏晚' }).click();
  await expect(page.getByRole('dialog')).not.toContainText(hiddenId);
  await page.getByRole('button', { name: '发送申请', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('好友申请已发送');
  expect(target).toBe(hiddenId);

  const job = {
    public_id: hiddenId,
    type: 'POLISH',
    status: 'COMPLETED',
    created_at: new Date().toISOString(),
  };
  await page.route('**/ai/jobs?*', (route) =>
    route.fulfill(fulfill({ items: [job], has_more: false })),
  );
  await page.route('**/ai/jobs/' + hiddenId, (route) =>
    route.fulfill(
      fulfill({
        ...job,
        output_payload: {
          user_public_id: hiddenId,
          result: { conversationId: hiddenId, message: '保留有用的润色结果' },
        },
      }),
    ),
  );
  await page.route('**/ai/profiles', (route) =>
    route.fulfill(
      fulfill({
        items: [
          {
            profile_type: 'PERSONA',
            updated_at: new Date().toISOString(),
            content: { user_id: hiddenId, traits: ['喜欢倾听'], nested: { jobId: hiddenId } },
          },
        ],
      }),
    ),
  );
  await page.goto('/#/insights');
  await page.locator('.job-button').first().click();
  await expect(page.getByRole('dialog')).toContainText('保留有用的润色结果');
  await expect(page.getByRole('dialog')).not.toContainText(hiddenId);
  await expect(page.getByRole('dialog')).not.toContainText('conversationId');
  await expect(page.getByRole('dialog')).not.toContainText('任务 ID');
  await page.getByRole('button', { name: '关闭任务详情', exact: true }).click();
  await page.getByRole('button', { name: '沟通画像', exact: true }).click();
  await expect(page.locator('.json-output')).toContainText('喜欢倾听');
  await expect(page.locator('main')).not.toContainText(hiddenId);
});
test('inline chat AI consumes SSE and keeps the draft on failure without navigating', async ({
  page,
}) => {
  await login(page);
  await page.goto('/#/messages');
  await page.locator('.chat-list .conversation-row').first().click();
  const input = page.getByLabel('消息内容');
  await input.fill('想聊聊这次方案');
  await page.route('**/ai/interactions/polish', async (route) => {
    expect(route.request().postDataJSON().message).toBe('想聊聊这次方案');
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'data: {"output":"我很期待"}\n\ndata: {"output":"与你讨论这次方案。"}\n\n',
    });
  });
  await page.getByRole('button', { name: 'AI 润色', exact: true }).click();
  await expect(input).toHaveValue('我很期待与你讨论这次方案。');
  await expect(page).toHaveURL(/messages/);
  await page.getByRole('button', { name: '撤销润色', exact: true }).click();
  await expect(input).toHaveValue('想聊聊这次方案');
  await page.unroute('**/ai/interactions/polish');
  await page.route('**/ai/interactions/polish', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 400, msg: '模型暂不可用', error_code: 'MODEL_OPTION_INVALID' }),
    }),
  );
  await page.getByRole('button', { name: 'AI 润色', exact: true }).click();
  await expect(page.locator('.chat-ai-tray').getByRole('alert')).toContainText(
    '暂时无法润色，原文已保留',
  );
  await expect(input).toHaveValue('想聊聊这次方案');
  await expect(page).toHaveURL(/messages/);
  await page.route('**/ai/interactions/summarize', (route) => {
    expect(route.request().postDataJSON().conversation_public_id).toBeTruthy();
    return route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'data: {"output":"这次讨论约定了下一步计划。"}\n\n',
    });
  });
  await page.getByRole('button', { name: '总结对话', exact: true }).click();
  await expect(page.locator('.chat-ai-output')).toHaveText('这次讨论约定了下一步计划。');
  await expect(page).toHaveURL(/messages/);
});
test('switching conversations cancels pending polishing and preserves separate drafts', async ({
  page,
}) => {
  await login(page);
  await page.goto('/#/messages');
  const rows = page.locator('.chat-list .conversation-row');
  await rows.first().click();
  await page.getByLabel('消息内容').fill('这是第一段会话的草稿');
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => (release = resolve));
  let complete: () => void = () => {};
  const settled = new Promise<void>((resolve) => (complete = resolve));
  await page.route('**/ai/interactions/polish', async (route) => {
    await gate;
    try {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: 'data: {"output":"过期的润色结果"}\n\n',
      });
    } catch {
    } finally {
      complete();
    }
  });
  await page.getByRole('button', { name: 'AI 润色', exact: true }).click();
  await expect(page.locator('.chat-ai-progress')).toBeVisible();
  await rows.nth(1).click();
  release();
  await settled;
  await expect(page.getByLabel('消息内容')).toHaveValue('');
  await expect(page.locator('.chat-ai-tray')).toHaveCount(0);
  await expect(page.getByLabel('消息内容')).not.toHaveAttribute('readonly', '');
  await rows.first().click();
  await expect(page.getByLabel('消息内容')).toHaveValue('这是第一段会话的草稿');
});
test('incoming messages update unread badges and opening the chat clears them', async ({
  page,
}) => {
  await login(page);
  await page.goto('/#/messages');
  await expect(page.locator('.connection')).toHaveText('实时在线');
  const token = await page.evaluate(() => sessionStorage.getItem('access_token'));
  const bLogin = await page.request.post('http://127.0.0.1:8082/api/v1/auth/login', {
    data: { username: accounts[1].username, password: accounts[1].password },
  });
  const b = (await bLogin.json()).data;
  const me = await page.request.get('/api/v1/users/me', {
    headers: { Authorization: 'Bearer ' + b.access_token },
  });
  const peer = (await me.json()).data;
  const conv = await page.request.post('/api/v1/conversations', {
    headers: { Authorization: 'Bearer ' + token },
    data: { target_user_public_id: peer.public_id },
  });
  const id = (await conv.json()).data;
  const sent = await page.request.post(`/api/v1/conversations/${id}/messages`, {
    headers: { Authorization: 'Bearer ' + b.access_token },
    data: { content: '未读提醒验证 ' + Date.now(), media_type: 'TEXT' },
  });
  expect((await sent.json()).code).toBeLessThan(400);
  const row = page.locator('.chat-list .conversation-row').filter({ hasText: '未读提醒验证' });
  await expect(row.locator('.conversation-unread')).toBeVisible();
  await row.click();
  await expect(row.locator('.conversation-unread')).toHaveCount(0);
});
test('live group lifecycle, member roles, chat, file and settings', async ({ page, browser }) => {
  test.setTimeout(90000);
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await login(page);
  await page.getByRole('navigation').getByRole('button', { name: '群聊', exact: true }).click();
  await page.getByRole('button', { name: '创建群聊', exact: true }).click();
  const name = '浏览器联调群 ' + Date.now();
  await page.getByLabel('群聊名称', { exact: true }).fill(name);
  await page.getByRole('dialog').getByLabel('联调 · 陈一诺', { exact: true }).check();
  await page.getByRole('dialog').getByRole('button', { name: '创建群聊', exact: true }).click();
  await expect(page.locator('.group-detail h2')).toHaveText(name);
  await expect(page.locator('.detail-description')).toContainText('2 位成员');
  await page.getByRole('button', { name: '邀请好友', exact: true }).click();
  await page.getByRole('dialog').getByLabel('联调 · 许知远', { exact: true }).check();
  await page.getByRole('button', { name: '邀请 1 位好友', exact: true }).click();
  await expect(page.locator('.member-row').filter({ hasText: '联调 · 许知远' })).toBeVisible();
  await expect(page.locator('.detail-description')).toContainText('3 位成员');
  await page
    .locator('.member-row')
    .filter({ hasText: '联调 · 许知远' })
    .getByRole('button', { name: '移出', exact: true })
    .click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(page.locator('.member-row').filter({ hasText: '联调 · 许知远' })).toHaveCount(0);
  const groupId = await page.evaluate(() => window.history.state.activeGroup);
  expect(groupId).toBeTruthy();
  await expect(page.locator('main')).not.toContainText(groupId);
  await expect(page).toHaveURL(/#\/groups$/);
  await page.reload();
  await expect(page.locator('.group-detail h2')).toHaveText(name);
  await page.getByRole('button', { name: '编辑群名', exact: true }).click();
  await page.getByLabel('群聊名称', { exact: true }).fill(name + ' 已更新');
  await page.getByRole('button', { name: '保存群名', exact: true }).click();
  await expect(page.locator('.group-detail h2')).toHaveText(name + ' 已更新');
  const member = page.locator('.member-row').filter({ hasText: '联调 · 陈一诺' });
  await member.getByRole('button', { name: '设为管理员', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(member.locator('.role')).toHaveText('管理员');
  await member.getByRole('button', { name: '取消管理员', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(member.locator('.role')).toHaveText('成员');
  await expect(page.locator('.loading')).toHaveCount(0);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: 'artifacts/live-group-desktop.png', fullPage: true });
  await page.getByRole('button', { name: '进入群聊', exact: true }).click();
  await expect(page.locator('.chat-heading h3')).toHaveText(name + ' 已更新');
  await page.getByLabel('消息内容').fill('真实后端浏览器群聊验证');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect(page.locator('.chat-messages')).toContainText('真实后端浏览器群聊验证');
  await page.getByLabel('上传聊天文件').setInputFiles({
    name: 'browser-check.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('browser file check'),
  });
  await expect(page.getByRole('region', { name: '待发送文件' })).toContainText('browser-check.txt');
  await page.getByRole('button', { name: '发送文件', exact: true }).click();
  await expect(page.locator('.file-message')).toContainText('browser-check.txt');
  const downloadPromise = page.waitForEvent('download');
  await page.locator('.file-message').last().click();
  const downloaded = await downloadPromise;
  expect(downloaded.suggestedFilename()).toBe('browser-check.txt');
  await page.reload();
  await expect(page.locator('.loading')).toHaveCount(0);
  await page
    .locator('.chat-list .conversation-row')
    .filter({ hasText: name + ' 已更新' })
    .click();
  await expect(page.locator('.chat-messages')).toContainText('真实后端浏览器群聊验证');
  await page.getByRole('button', { name: '群资料与成员' }).click();
  await expect(page.locator('.group-detail h2')).toHaveText(name + ' 已更新');
  await page
    .locator('.member-row')
    .filter({ hasText: '联调 · 陈一诺' })
    .getByRole('button', { name: '转让群主', exact: true })
    .click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(
    page.locator('.member-row').filter({ hasText: '联调 · 陈一诺' }).locator('.role'),
  ).toHaveText('群主');
  await page.getByRole('button', { name: '退出群聊', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(page.locator('.group-detail')).toHaveCount(0);
  const context = await browser.newContext({ baseURL: 'http://127.0.0.1:5186' });
  const second = await context.newPage();
  await login(second, 1);
  await second.goto('/#/groups?group=' + groupId);
  await expect(second.locator('.group-detail h2')).toHaveText(name + ' 已更新');
  await expect(second).toHaveURL(/#\/groups$/);
  await second.getByRole('button', { name: '解散群聊', exact: true }).click();
  await second.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(second.getByRole('dialog').getByRole('alert')).toContainText('服务器内部错误');
  await expect(second.locator('.group-detail')).toHaveCount(1);
  await second.getByRole('button', { name: '关闭弹窗', exact: true }).click();
  await second.getByRole('button', { name: '创建群聊', exact: true }).click();
  await second.getByLabel('群聊名称', { exact: true }).fill('可解散空群 ' + Date.now());
  await second.getByRole('dialog').getByRole('button', { name: '创建群聊', exact: true }).click();
  await second.getByRole('button', { name: '解散群聊', exact: true }).click();
  await second.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(second.locator('.group-detail')).toHaveCount(0);
  await context.close();
  expect(errors).toEqual([]);
});
test('live AI activity exposes backend failures and all pages fit mobile', async ({ page }) => {
  await login(page);
  await page.goto('/#/insights');
  await page.getByRole('button', { name: '使用统计', exact: true }).click();
  await expect(page.locator('.usage-stats')).toBeVisible();
  await page.getByRole('button', { name: '沟通画像', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.locator('.error')).toHaveCount(0);
  await page.setViewportSize({ width: 375, height: 900 });
  for (const path of [
    '/groups',
    '/friends',
    '/messages',
    '/schedules',
    '/ai',
    '/insights',
    '/settings',
  ]) {
    await page.goto('/#' + path);
    await expect(page.locator('.page-heading')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  }
  await page.goto('/#/groups');
  await page.locator('.group-card').first().click();
  await expect(page.locator('.group-detail')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'artifacts/live-group-mobile.png', fullPage: true });
});
test('live sign out protects private routes and registration reports errors', async ({ page }) => {
  await login(page);
  await page.goto('/#/settings');
  await expect(page.getByLabel('昵称', { exact: true })).toHaveValue('联调 · 林沐');
  await expect(page.getByRole('heading', { name: '账户安全', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '退出登录', exact: true }).click();
  await expect(page).toHaveURL(/login/);
  await page.goto('/#/groups');
  await expect(page).toHaveURL(/login/);
  await page.getByLabel('用户名', { exact: true }).fill(accounts[0].username);
  await page.getByLabel('密码', { exact: true }).fill('Incorrect1!');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('用户名或密码错误');
  await expect(page).toHaveURL(/login/);
});
