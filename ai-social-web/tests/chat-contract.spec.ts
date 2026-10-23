import { test, expect } from '@playwright/test';

test('forward retries only unsent messages and copies attachments to the destination conversation', async ({
  page,
}) => {
  const me = { public_id: 'person-me', nickname: '林沐', username: 'linmu' };
  const friend = { public_id: 'person-other', nickname: '陈一诺' };
  const conversations = [
    { public_id: 'source', type: 'PRIVATE', name: '陈一诺', unread_count: 0 },
    { public_id: 'target', type: 'GROUP', name: '接收群', unread_count: 0 },
  ];
  const source = [
    {
      public_id: 'file-message',
      media_type: 'FILE',
      sender: friend,
      created_at: new Date().toISOString(),
      file: { public_id: 'old-file', original_filename: '讨论资料.txt', size_bytes: 10 },
    },
    {
      public_id: 'text-message',
      media_type: 'TEXT',
      sender: friend,
      content: '明天下午讨论方案',
      created_at: new Date().toISOString(),
    },
  ];
  let uploads = 0,
    sends = 0,
    failOnce = true;
  const sentBodies: any[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname.replace('/api/v1', '');
    const fulfill = (data: unknown) =>
      route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) });
    if (path === '/auth/login')
      return fulfill({ access_token: 'test-access', refresh_token: 'test-refresh' });
    if (path === '/users/me') return fulfill(me);
    if (path === '/conversations') return fulfill({ items: conversations, has_more: false });
    if (path === '/friends') return fulfill([friend]);
    if (path === '/schedules') return fulfill([]);
    if (path === '/ai/jobs') return fulfill({ items: [], has_more: false });
    if (path === '/friends/requests') return fulfill({ incoming: [], outgoing: [] });
    if (path === '/ai/models') return fulfill({ models: [] });
    if (path === '/sse/subscribe')
      return route.fulfill({ contentType: 'text/event-stream', body: ': keepalive\n\n' });
    if (path === '/conversations/source/messages')
      return fulfill({ items: source, has_more: false });
    if (path === '/files/old-file/content')
      return route.fulfill({ contentType: 'text/plain', body: 'test attachment' });
    if (path === '/files' && request.method() === 'POST') {
      uploads++;
      expect(request.postData()).toContain('target');
      return fulfill({ public_id: 'new-file' });
    }
    if (path === '/conversations/target/messages' && request.method() === 'POST') {
      sends++;
      const body = request.postDataJSON();
      sentBodies.push(body);
      if (body.media_type === 'FILE' && failOnce) {
        failOnce = false;
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ code: 429, msg: '发送过于频繁' }),
        });
      }
      return fulfill({
        public_id: 'sent-' + sends,
        sender: me,
        content: body.content,
        media_type: body.media_type,
        created_at: new Date().toISOString(),
      });
    }
    throw new Error(`unexpected API ${request.method()} ${path}`);
  });
  await page.goto('/#/login');
  await page.getByLabel('用户名', { exact: true }).fill('test-user');
  await page.getByLabel('密码', { exact: true }).fill('Test123!');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.page-heading h1')).toContainText('你好');
  await page.goto('/#/messages');
  await page.locator('.conversation-row').filter({ hasText: '陈一诺' }).click();
  await expect(page.locator('.message')).toHaveCount(2);
  await page.locator('.message-more').first().click();
  await page.locator('.message').nth(1).getByRole('checkbox').check();
  await page.getByRole('toolbar').getByRole('button', { name: '转发', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('接收群', { exact: true }).check();
  await dialog.getByRole('button', { name: '确认转发', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('已发送 1/2 条');
  expect(uploads).toBe(1);
  expect(sends).toBe(2);
  expect(sentBodies[0].content).toContain('明天下午讨论方案');
  expect(sentBodies[1].file_public_id).toBe('new-file');
  await dialog.getByRole('button', { name: '重试剩余消息', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已转发 2 条消息');
  expect(uploads).toBe(1);
  expect(sends).toBe(3);
  expect(sentBodies[2].temp_id).toBe(sentBodies[1].temp_id);
  expect(sentBodies[2].file_public_id).toBe('new-file');
});
