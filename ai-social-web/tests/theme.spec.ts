import { expect, test } from '@playwright/test';

test('主题跟随系统、支持手动切换并在刷新和新标签页中保持', async ({ page, context }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/#/settings');
  const root = page.locator('html');
  await expect(root).toHaveAttribute('data-theme', 'light');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(root).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: '浅色', exact: true }).click();
  await expect(root).toHaveAttribute('data-theme', 'light');
  await page.reload();
  await expect(root).toHaveAttribute('data-theme', 'light');
  await page.getByRole('button', { name: '切换为深色模式' }).click();
  await expect(root).toHaveAttribute('data-theme', 'dark');
  const other = await context.newPage();
  await other.goto('/#/settings');
  await expect(other.locator('html')).toHaveAttribute('data-theme', 'dark');
  await other.getByRole('button', { name: '浅色', exact: true }).click();
  await expect(root).toHaveAttribute('data-theme', 'light');
  await page.getByRole('button', { name: '跟随系统', exact: true }).click();
  await expect(root).toHaveAttribute('data-theme', 'dark');
  await page.emulateMedia({ colorScheme: 'light' });
  await expect(root).toHaveAttribute('data-theme', 'light');
});

test('深色页面、聊天和弹窗在桌面与手机下保持布局及可读配色', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  for (const width of [375, 573, 1440]) {
    await page.setViewportSize({ width, height: 891 });
    for (const [route, title] of [
      ['', '我的概览'],
      ['messages', '消息'],
      ['friends', '好友'],
      ['groups', '群聊'],
      ['schedules', '日程'],
      ['ai', 'AI 助手'],
      ['insights', 'AI 活动'],
      ['settings', '个人设置'],
      ['login', '登录共鸣'],
    ]) {
      await page.goto(`/#/${route}`);
      await expect(page.locator('h1')).toContainText(title);
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      const surface = page.locator('.card').first();
      if (await surface.count())
        await expect(surface).not.toHaveCSS('background-color', 'rgb(255, 255, 255)');
    }
    await page.goto('/#/messages');
    await page.locator('.conversation-row').first().click();
    await expect(page.locator('.message p').first()).not.toHaveCSS(
      'background-color',
      'rgb(255, 255, 255)',
    );
    await page.screenshot({ path: `.local/dark-messages-${width}.png`, fullPage: true });
    await page.goto('/#/groups');
    await page.locator('.group-card').first().click();
    await expect(page.locator('.group-detail')).toBeVisible();
    await page.screenshot({ path: `.local/dark-group-detail-${width}.png`, fullPage: true });
    await page.goto('/#/schedules');
    await page.getByRole('button', { name: '新建日程' }).click();
    await page.getByRole('button', { name: '选择开始时间', exact: true }).click();
    await expect(page.getByRole('region', { name: '开始时间选择器' })).toBeVisible();
    await expect(page.getByRole('dialog')).not.toHaveCSS('background-color', 'rgb(255, 255, 255)');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: `.local/dark-date-picker-${width}.png`, fullPage: true });
    await page.getByRole('button', { name: '关闭弹窗', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  }
  expect(errors).toEqual([]);
});
