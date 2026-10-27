import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/lib/refresh.ts', import.meta.url), 'utf8');
const js = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText;
const { createCoalescedRefresh } = await import(
  'data:text/javascript;base64,' + Buffer.from(js).toString('base64')
);

test('a burst runs one refresh at a time with at most one pending refresh', async () => {
  const releases = [];
  let calls = 0,
    active = 0,
    maximum = 0;
  const refresh = createCoalescedRefresh(async () => {
    calls++;
    maximum = Math.max(maximum, ++active);
    await new Promise((resolve) => releases.push(resolve));
    active--;
  });
  const first = refresh();
  await Promise.resolve();
  for (let i = 0; i < 100; i++) assert.equal(refresh(), first);
  assert.equal(calls, 1);
  releases.shift()();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(calls, 2);
  releases.shift()();
  await first;
  assert.equal(calls, 2);
  assert.equal(maximum, 1);
});

test('a rejected refresh is reported and a later trigger can recover', async () => {
  let calls = 0;
  const refresh = createCoalescedRefresh(async () => {
    if (++calls === 1) throw new Error('temporary failure');
  });
  await assert.rejects(refresh(), /temporary failure/);
  await refresh();
  assert.equal(calls, 2);
});

test('notifications in the same turn are combined before starting work', async () => {
  let calls = 0;
  const refresh = createCoalescedRefresh(async () => {
    calls++;
  });
  await Promise.all(Array.from({ length: 100 }, () => refresh()));
  assert.equal(calls, 1);
});

test('a notification arriving as the previous refresh settles is not lost', async () => {
  let calls = 0;
  const refresh = createCoalescedRefresh(async () => {
    if (++calls === 1)
      queueMicrotask(() =>
        queueMicrotask(() => {
          void refresh();
        }),
      );
  });
  await refresh();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(calls, 2);
});

test('a failed running refresh clears its combined pending request before later recovery', async () => {
  let calls = 0,
    reject;
  const refresh = createCoalescedRefresh(async () => {
    if (++calls === 1)
      await new Promise((_, fail) => {
        reject = fail;
      });
  });
  const first = refresh();
  await Promise.resolve();
  assert.equal(refresh(), first);
  reject(new Error('failed batch'));
  await assert.rejects(first, /failed batch/);
  await refresh();
  assert.equal(calls, 2);
});

test('refresh diagnostics expose immutable counts of requests combined into actual runs', async () => {
  const refresh = createCoalescedRefresh(async () => {});
  const snapshots = refresh.diagnostics();
  await Promise.all(Array.from({ length: 100 }, () => refresh()));
  assert.deepEqual(refresh.diagnostics(), {
    requests: 100,
    runs: 1,
    coalescedRequests: 99,
    failures: 0,
    running: false,
    pending: false,
  });
  assert.equal(snapshots.requests, 0);
  assert.ok(Object.isFrozen(snapshots));
  const failing = createCoalescedRefresh(async () => {
    throw new Error('refresh failed');
  });
  await assert.rejects(failing(), /refresh failed/);
  assert.equal(failing.diagnostics().failures, 1);
  assert.equal(failing.diagnostics().running, false);
});
