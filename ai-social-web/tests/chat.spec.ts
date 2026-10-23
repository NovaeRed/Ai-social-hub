import { test, expect, type Locator, type Page } from '@playwright/test';

async function swipe(page: Page, row: Locator) {
  const box = await row.boundingBox();
  await page.mouse.move(box!.x + box!.width - 8, box!.y + 18);
  await page.mouse.down();
  await page.mouse.move(box!.x + 8, box!.y + 18, { steps: 8 });
  await page.mouse.up();
}

for (const width of [375, 580, 1024]) {
  test(`conversation swipe, pin, local removal and message multi-select at ${width}`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.setViewportSize({ width, height: 892 });
    await page.goto('/#/messages');
    const item = page.locator('.conversation-item').filter({ hasText: '苏小满' });
    await swipe(page, item.locator('.conversation-row'));
    await expect(item.getByRole('button', { name: '置顶聊天', exact: true })).toBeVisible();
    await expect(page.locator('.chat-heading')).toHaveCount(0);
    await item.getByRole('button', { name: '置顶聊天', exact: true }).click();
    await expect(page.locator('.conversation-item').first()).toContainText('苏小满');
    await page.reload();
    await expect(page.locator('.conversation-item').first()).toContainText('苏小满');
    await page.getByRole('button', { name: '会话操作：苏小满', exact: true }).click();
    await page.getByRole('button', { name: '取消置顶', exact: true }).click();
    await expect(page.locator('.conversation-item').first()).toContainText('陈一诺');
    await page.getByRole('button', { name: '会话操作：陈一诺', exact: true }).click();
    await page.getByRole('button', { name: '移除会话', exact: true }).click();
    await expect(page.getByRole('dialog')).toContainText('不删除聊天记录');
    await page.getByRole('button', { name: '确认移除会话', exact: true }).click();
    await page.reload();
    await expect(page.locator('.chat-list .conversation-row')).toHaveCount(3);
    await expect(page.getByRole('button', { name: '会话操作：陈一诺', exact: true })).toHaveCount(
      0,
    );
    await page.goto('/#/friends');
    await page
      .locator('.friend-card')
      .filter({ hasText: '陈一诺' })
      .getByRole('button', { name: '发消息' })
      .click();
    await expect(page.locator('.chat-list .conversation-row')).toHaveCount(4);
    await expect(
      page
        .locator('.chat-list .conversation-row')
        .filter({ hasText: '陈一诺' })
        .locator('.conversation-unread'),
    ).toHaveCount(0);
    await expect(page.locator('.message')).toHaveCount(3);
    const first = page.locator('.message-body p').first();
    await first.scrollIntoViewIfNeeded();
    const box = await first.boundingBox();
    await page.mouse.move(box!.x + 10, box!.y + 10);
    await page.mouse.down();
    await page.mouse.move(box!.x + 10, box!.y + 35, { steps: 4 });
    await page.waitForTimeout(550);
    await page.mouse.up();
    await expect(page.getByRole('toolbar', { name: '消息多选操作' })).toHaveCount(0);
    await first.scrollIntoViewIfNeeded();
    const pressed = await first.boundingBox();
    await page.mouse.move(pressed!.x + 10, pressed!.y + 10);
    await page.mouse.down();
    await page.waitForTimeout(550);
    await page.mouse.up();
    const toolbar = page.getByRole('toolbar', { name: '消息多选操作' });
    await expect(toolbar).toContainText('已选 1 条');
    await page.locator('.message').nth(1).getByRole('checkbox').check();
    await expect(toolbar).toContainText('已选 2 条');
    await page.screenshot({ path: `artifacts/chat-multiselect-${width}.png`, fullPage: true });
    await toolbar.getByRole('button', { name: '转发', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('转发 2 条消息');
    await page.screenshot({ path: `artifacts/chat-forward-dialog-${width}.png`, fullPage: true });
    expect(
      await dialog.evaluate((element) => element.parentElement?.parentElement === document.body),
    ).toBe(true);
    await dialog.getByLabel('苏小满', { exact: true }).check();
    await dialog.getByRole('button', { name: '确认转发', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('已转发 2 条消息');
    await page
      .getByRole('button', {
        name: '消息操作：最近发现了一个很棒的艺术展，感觉你会喜欢。',
        exact: true,
      })
      .click();
    await page.locator('.message').nth(1).getByRole('checkbox').check();
    await toolbar.getByRole('button', { name: '删除', exact: true }).click();
    await expect(dialog).toContainText('当前账号在此浏览器');
    await dialog.getByRole('button', { name: '确认删除消息', exact: true }).click();
    await expect(page.locator('.message')).toHaveCount(1);
    await page.reload();
    await page.locator('.chat-list .conversation-row').filter({ hasText: '陈一诺' }).click();
    await expect(page.locator('.message')).toHaveCount(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: `artifacts/chat-actions-${width}.png`, fullPage: true });
    expect(errors).toEqual([]);
  });
}

test('invalid stored preferences do not crash chat and selected messages can be cancelled', async ({
  page,
}) => {
  await page.addInitScript(() =>
    localStorage.setItem(
      'chat-preferences:demo-me',
      JSON.stringify({
        pinned: [null, 1],
        removed: { wrong: [] },
        hidden: { 'demo-conv-1': 'corrupted' },
      }),
    ),
  );
  await page.goto('/#/messages');
  await page.locator('.chat-list .conversation-row').first().click();
  await expect(page.locator('.message')).toHaveCount(3);
  await page.locator('.message-more').first().click();
  await expect(page.getByRole('toolbar')).toContainText('已选 1 条');
  await page.locator('.message-choice').first().uncheck();
  await expect(page.getByRole('toolbar')).toContainText('已选 0 条');
  await expect(
    page.getByRole('toolbar').getByRole('button', { name: '删除', exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('toolbar').getByRole('button', { name: '转发', exact: true }),
  ).toBeDisabled();
  await page.getByRole('toolbar').getByRole('button', { name: '取消', exact: true }).click();
  await expect(page.getByRole('toolbar')).toHaveCount(0);
});

test.describe('real touch interactions', () => {
  test.use({ hasTouch: true });
  test('horizontal touch reveals actions and long touch selects without swallowing the next message', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 580, height: 892 });
    await page.goto('/#/messages');
    const session = await page.context().newCDPSession(page);
    const row = page.locator('.conversation-item').first();
    const box = await row.boundingBox();
    const point = { x: box!.x + box!.width - 35, y: box!.y + 30 };
    await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: point.x - 70, y: point.y }],
    });
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await expect(row.getByRole('button', { name: '置顶聊天', exact: true })).toBeVisible();
    await row.getByRole('button', { name: '会话操作：陈一诺', exact: true }).tap();
    await row.locator('.conversation-row').tap();
    const first = page.locator('.message-body p').first();
    await first.scrollIntoViewIfNeeded();
    const message = await first.boundingBox();
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: [{ x: message!.x + 12, y: message!.y + 12 }],
    });
    await page.waitForTimeout(550);
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await expect(page.getByRole('toolbar')).toContainText('已选 1 条');
    await page.locator('.message').nth(1).getByRole('checkbox').tap();
    await expect(page.getByRole('toolbar')).toContainText('已选 2 条');
    await session.detach();
  });
});

test('selection limit stays at 20 for checkboxes and context menu paths', async ({ page }) => {
  await page.goto('/#/messages');
  await page.locator('.chat-list .conversation-row').first().click();
  for (let i = 0; i < 19; i++) {
    await page.getByLabel('消息内容').fill('选择上限消息 ' + i);
    await page.getByRole('button', { name: '发送', exact: true }).click();
  }
  await expect(page.locator('.message')).toHaveCount(22);
  await page.locator('.message-more').first().click();
  for (let i = 1; i < 20; i++) await page.locator('.message').nth(i).getByRole('checkbox').check();
  await page.locator('.message').nth(20).getByRole('checkbox').click();
  await expect(page.locator('.message').nth(20).getByRole('checkbox')).not.toBeChecked();
  await page.locator('.message').nth(21).dispatchEvent('contextmenu');
  await expect(page.getByRole('toolbar')).toContainText('已选 20 条');
  await expect(page.locator('.message-choice:checked')).toHaveCount(20);
});
