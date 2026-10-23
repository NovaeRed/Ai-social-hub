import { test } from 'node:test';
import assert from 'node:assert/strict';
import { presentation } from './presentation-module.mjs';
test('task and profile results hide nested identifiers while retaining useful content', () => {
  const result = JSON.parse(
    presentation.readableJson({
      public_id: 'demo-task-1',
      title: '会议纪要',
      items: [
        { sourceMessageId: 123, source_message_id: 456, user_id: 'demo-user-1', text: '明天见' },
      ],
      total_tokens: 100,
    }),
  );
  assert.deepEqual(result, { title: '会议纪要', items: [{ text: '明天见' }], total_tokens: 100 });
});
test('error messages and generated nicknames do not reveal technical IDs', () => {
  const id = '01M5KHE3Y3H8WT63RSJYE63CFJ';
  assert.equal(presentation.redactIdentifiers('群 ' + id + ' 暂不可用'), '群 关联记录 暂不可用');
  assert.equal(
    presentation.displayUserName({ nickname: 'AIHub_' + id, username: 'xiaoman' }),
    'xiaoman',
  );
  assert.equal(presentation.displayUserName({ nickname: id }, '群成员'), '群成员');
  assert.equal(
    presentation.redactIdentifiers('user_550e8400-e29b-41d4-a716-446655440000'),
    '关联记录',
  );
});
