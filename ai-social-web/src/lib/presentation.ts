const technicalId =
  /\b(?:[a-z][a-z0-9]*_)?(?:[0-9A-HJKMNP-TV-Z]{26}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\b/gi;

interface PreviewMessage {
  content?: string;
  media_type?: string;
  file?: { original_filename?: string };
}

/** 正文为空的附件仍是一条消息，只有缺少消息记录时才显示空状态。 */
export function formatMessagePreview(message?: PreviewMessage | null): string {
  if (!message) return '暂无消息';
  if (message.content?.trim()) return message.content;
  if (message.media_type === 'FILE' || message.file) {
    const filename = message.file?.original_filename?.trim();
    return filename ? `[文件] ${filename}` : '[文件]';
  }
  const mediaLabels: Record<string, string> = {
    IMAGE: '[图片]',
    VOICE: '[语音]',
    VIDEO: '[视频]',
  };
  return mediaLabels[message.media_type || ''] || '[消息]';
}

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
