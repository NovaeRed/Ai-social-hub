const technicalId =
  /\b(?:[a-z][a-z0-9]*_)?(?:[0-9A-HJKMNP-TV-Z]{26}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\b/gi;

export function redactIdentifiers(text: string): string {
  return text.replace(technicalId, '关联记录');
}

export function displayUserName(
  user: { nickname?: string; username?: string },
  fallback = '用户',
): string {
  for (const candidate of [user.nickname, user.username]) {
    const name = candidate?.trim();
    if (name && redactIdentifiers(name) === name && !/^AIHub_/.test(name)) return name;
  }
  return fallback;
}

function readableValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(readableValue);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .filter(([key]) => !/^(id|ids)$/i.test(key) && !/(?:_ids?|Ids?|IDs?)$/.test(key))
        .map(([key, item]) => [key, readableValue(item)]),
    );
  }
  return typeof value === 'string' ? redactIdentifiers(value) : value;
}

export function readableJson(value: unknown): string {
  return JSON.stringify(readableValue(value), null, 2) || '';
}
