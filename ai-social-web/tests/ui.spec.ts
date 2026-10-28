import { test, expect } from '@playwright/test';

test('settings validation stays in its form and schedule dates avoid native segment selection', async ({
  page,
}) => {
  await page.setViewportSize({ width: 580, height: 892 });
  await page.goto('/#/settings');
  await page.getByLabel('手机号', { exact: true }).fill('123');
  await page.getByRole('button', { name: '保存联系方式' }).click();
  const contact = page.locator('.account-section').filter({ hasText: '头像与联系方式' });
  await expect(contact.getByRole('alert')).toHaveText('请填写有效的中国大陆手机号');
  await expect(
    page.locator('.account-section').filter({ hasText: '账户安全' }).getByRole('alert'),
  ).toHaveCount(0);
  await expect(page.locator('.page-content > .inline-error')).toHaveCount(0);
  await contact.scrollIntoViewIfNeeded();
  await page.screenshot({ path: 'artifacts/settings-local-validation.png', fullPage: true });
  await page.goto('/#/schedules');
  await page.getByRole('button', { name: '新建日程' }).click();
  await page.getByLabel('日程标题').fill('自定义日期控件验证');
  const start = page.getByLabel('开始时间', { exact: true });
  await expect(start).toHaveAttribute('type', 'text');
  await start.fill('2026-10-23 14:00');
  await page.getByLabel('结束时间', { exact: true }).fill('2026-10-23 13:00');
  await page.getByRole('button', { name: '保存日程' }).click();
  await expect(page.getByRole('dialog')).toContainText('结束时间晚于开始时间');
  await page.getByRole('button', { name: '选择结束时间', exact: true }).click();
  const picker = page.getByRole('region', { name: '结束时间选择器' });
  await expect(picker).toBeVisible();
  await page.screenshot({ path: 'artifacts/schedule-picker-expanded.png', fullPage: true });
  await picker.getByRole('combobox', { name: '时', exact: true }).selectOption('15');
  await picker.getByRole('button', { name: '确定时间' }).click();
  await expect(page.getByLabel('结束时间', { exact: true })).toHaveValue('2026-10-23 15:00');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'artifacts/schedule-date-picker.png', fullPage: true });
  await page.getByRole('button', { name: '保存日程' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('group and contact details hide IDs and retain group selection after refresh', async ({
  page,
}) => {
  await page.goto('/#/groups');
  await page.locator('.group-card').first().click();
  await expect(page.locator('.group-detail h2')).toHaveText('灵感碰撞小分队');
  await expect(page.locator('main')).not.toContainText('demo-conv-2');
  await expect(page.locator('.copy-id')).toHaveCount(0);
  await expect(page).toHaveURL(/#\/groups$/);
  await page.reload();
  await expect(page.locator('.group-detail h2')).toHaveText('灵感碰撞小分队');
  await page.getByRole('button', { name: '关闭群详情' }).click();
  await page.reload();
  await expect(page.locator('.group-detail')).toHaveCount(0);
  await page.goto('/#/friends');
  await page.getByRole('button', { name: '查看资料' }).first().click();
  await expect(page.getByRole('dialog')).not.toContainText('demo-1');
  await expect(page.getByRole('dialog')).not.toContainText('公开 ID');
});

test('mobile sidebar closes when tapping outside and settings hide account IDs', async ({
  page,
}) => {
  await page.setViewportSize({ width: 580, height: 892 });
  await page.goto('/');
  await page.getByRole('button', { name: '展开菜单', exact: true }).click();
  await expect(page.locator('.sidebar')).toHaveClass(/expanded/);
  await page.locator('.sidebar-backdrop').click({ position: { x: 540, y: 150 } });
  await expect(page.locator('.sidebar')).not.toHaveClass(/expanded/);
  await expect(page.getByRole('button', { name: '展开菜单', exact: true })).toHaveAttribute(
    'aria-expanded',
    'false',
  );
  await page.getByRole('button', { name: '展开菜单', exact: true }).click();
  await page.keyboard.press('Escape');
  await expect(page.locator('.sidebar')).not.toHaveClass(/expanded/);
  await page.goto('/#/settings');
  await expect(page.locator('main')).not.toContainText('公开 ID');
  await expect(page.locator('main')).not.toContainText('demo-me');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
test('dashboard, chat, AI, friends, schedules and profile work', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '我的概览' })).toBeVisible();
  await page.locator('.recent .conversation-row').first().click();
  await page.getByRole('textbox', { name: '消息内容' }).fill('这是一条交互验证消息');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect(page.locator('.chat-messages')).toContainText('这是一条交互验证消息');
  await page.getByRole('textbox', { name: '消息内容' }).fill('我们明天下午一起讨论吧');
  await page.getByRole('button', { name: 'AI 润色', exact: true }).click();
  await expect(page).toHaveURL(/messages/);
  await expect(page.getByRole('textbox', { name: '消息内容' })).toHaveValue(/演示润色/);
  await page.getByRole('button', { name: '撤销润色', exact: true }).click();
  await expect(page.getByRole('textbox', { name: '消息内容' })).toHaveValue(
    '我们明天下午一起讨论吧',
  );
  await page.locator('nav button').filter({ hasText: '好友' }).click();
  await page.getByRole('textbox', { name: '搜索好友' }).fill('陈一诺');
  await expect(page.locator('.friend-card')).toHaveCount(1);
  await page.getByRole('button', { name: '添加好友' }).click();
  await page.getByRole('button', { name: '同群联系人', exact: true }).click();
  await page.getByLabel('搜索联系人').fill('顾星河');
  await page.getByRole('button', { name: '顾星河', exact: false }).click();
  await page.getByLabel('打招呼').fill('你好');
  await page.getByRole('button', { name: '发送申请' }).click();
  await expect(page.getByRole('status')).toContainText('好友申请已发送');
  await page.locator('nav button').filter({ hasText: '日程' }).click();
  await page.getByRole('button', { name: '新建日程' }).click();
  await page.getByLabel('日程标题').fill('浏览器验证日程');
  await page.getByRole('button', { name: '保存日程' }).click();
  await expect(page.locator('.day-agenda')).toContainText('浏览器验证日程');
  await page.getByRole('button', { name: /浏览器验证日程/ }).click();
  await page.getByLabel('日程标题').fill('已更新日程');
  await page.getByRole('button', { name: '保存日程' }).click();
  await expect(page.locator('.day-agenda')).toContainText('已更新日程');
  await page.getByRole('button', { name: /已更新日程/ }).click();
  await page.getByRole('button', { name: '删除此日程' }).click();
  await expect(page.locator('.day-agenda')).not.toContainText('已更新日程');
  await page.locator('.sidebar-bottom > button').first().click();
  await page.getByLabel('昵称', { exact: true }).fill('新昵称');
  await page.getByRole('button', { name: '保存设置' }).click();
  await expect(page.locator('.sidebar-user')).toContainText('新昵称');
  expect(errors).toEqual([]);
});
for (const width of [375, 580, 1024]) {
  test(`chat stays side by side with unread badges and inline AI at ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 892 });
    await page.goto('/#/messages');
    const first = page.locator('.chat-list .conversation-row').first();
    await expect(first.getByLabel('2条未读')).toBeVisible();
    await first.click();
    await expect(first.locator('.conversation-unread')).toHaveCount(0);
    await expect(page.locator('.chat-list').getByLabel('5条未读')).toBeVisible();
    const list = await page.locator('.chat-list').boundingBox(),
      panel = await page.locator('.chat-panel').boundingBox();
    expect(panel!.x).toBeGreaterThanOrEqual(list!.x + list!.width - 1);
    expect(Math.abs(panel!.y - list!.y)).toBeLessThan(2);
    await page.getByLabel('消息内容').fill('周末我们一起看展吧');
    await page.getByRole('button', { name: 'AI 润色', exact: true }).click();
    await expect(page.getByLabel('消息内容')).toHaveValue(/演示润色/);
    await expect(page).toHaveURL(/messages/);
    await page.getByRole('button', { name: '撤销润色', exact: true }).click();
    await expect(page.getByLabel('消息内容')).toHaveValue('周末我们一起看展吧');
    await page.getByRole('button', { name: '回复建议', exact: true }).click();
    await expect(page.locator('.chat-ai-output')).toContainText('演示回复');
    await page.getByRole('button', { name: '使用回复', exact: true }).click();
    await expect(page.getByLabel('消息内容')).toHaveValue(/演示回复/);
    await page.getByRole('button', { name: '总结对话', exact: true }).click();
    await expect(page.locator('.chat-ai-output')).toContainText('演示总结');
    await expect(page).toHaveURL(/messages/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: `artifacts/chat-inline-${width}.png`, fullPage: true });
  });
}
for (const width of [375, 768, 1024, 1440]) {
  test(`responsive layout ${width}`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    for (const path of [
      '/',
      '/messages',
      '/friends',
      '/groups',
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
    await page.goto('/');
    await page.screenshot({ path: `artifacts/dashboard-${width}.png`, fullPage: true });
  });
}
