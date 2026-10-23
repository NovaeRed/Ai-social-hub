import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
import { presentationUrl } from './presentation-module.mjs';
const source = await readFile(new URL('../src/lib/identity.ts', import.meta.url), 'utf8');
const js = ts
  .transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  })
  .outputText.replace(/(['"])\.\/presentation\1/, JSON.stringify(presentationUrl));
const { normalizeCurrentUser } = await import(
  'data:text/javascript;base64,' + Buffer.from(js).toString('base64')
);
test('old generated nicknames display the name chosen during registration', () => {
  const original = {
    public_id: 'user_1',
    username: 'xiaoman',
    nickname: 'AIHub_01M5KG7E4TBNP8RHDB3NAQTRQK',
  };
  assert.equal(normalizeCurrentUser(original).nickname, 'xiaoman');
  assert.equal(original.nickname, 'AIHub_01M5KG7E4TBNP8RHDB3NAQTRQK');
});
test('a user chosen nickname takes priority over the username', () => {
  assert.equal(
    normalizeCurrentUser({ public_id: 'user_1', username: 'xiaoman', nickname: '小满' }).nickname,
    '小满',
  );
});
