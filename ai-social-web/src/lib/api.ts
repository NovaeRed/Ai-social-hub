export interface User {
  public_id: string;
  nickname: string;
  username?: string;
  email?: string;
  avatar_url?: string;
  ai_analysis_enabled?: boolean;
  phone?: string;
  vip_level?: string;
}
export interface Conversation {
  public_id: string;
  type: string;
  name: string;
  latest_message?: { public_id?: string; content: string; created_at?: string };
  unread_count: number;
}
export interface Message {
  public_id: string;
  sender: User;
  content: string;
  media_type?: string;
  media_url?: string;
  created_at?: string;
  file?: { public_id?: string; original_filename: string; access_url: string; size_bytes?: number };
}
export interface Schedule {
  public_id: string;
  title: string;
  description?: string;
  start_time: string;
  end_time: string;
  location?: string;
  is_ai_extracted?: boolean;
}
export interface Job {
  public_id: string;
  type: string;
  status: string;
  created_at: string;
  error_message?: string;
}
export const demo = import.meta.env.VITE_DEMO_MODE === 'true';
export const base = (import.meta.env.VITE_API_BASE || '/api/v1').replace(/\/$/, '');
let refreshFlight: Promise<void> | null = null;
export function tokens(data: { access_token: string; refresh_token: string }) {
  sessionStorage.setItem('access_token', data.access_token);
  sessionStorage.setItem('refresh_token', data.refresh_token);
}
export function clearTokens() {
  sessionStorage.removeItem('access_token');
  sessionStorage.removeItem('refresh_token');
}
export function unwrap(data: any) {
  if (
    data &&
    typeof data === 'object' &&
    typeof data.code === 'number' &&
    ('msg' in data || 'data' in data)
  ) {
    if (data.code >= 400) throw new Error(data.msg || '请求失败');
    return data.data;
  }
  return data;
}
async function refresh() {
  if (!refreshFlight)
    refreshFlight = (async () => {
      const response = await fetch(base + '/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${sessionStorage.getItem('access_token') || ''}`,
        },
        body: JSON.stringify({ refresh_token: sessionStorage.getItem('refresh_token') }),
        signal: AbortSignal.timeout(20000),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload.code >= 400 || !(payload.data || payload).access_token) {
        clearTokens();
        window.dispatchEvent(new Event('auth-expired'));
        throw new Error('登录已过期，请重新登录');
      }
      tokens(unwrap(payload));
    })().finally(() => {
      refreshFlight = null;
    });
  return refreshFlight;
}
export async function request(path: string, options: RequestInit = {}, retry = true): Promise<any> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const token = sessionStorage.getItem('access_token');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(base + path, {
    ...options,
    headers,
    signal: options.signal || AbortSignal.timeout(30000),
  });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.code >= 400) {
    const error = payload || {};
    const status = error.code >= 400 ? error.code : response.status;
    if (
      status === 401 &&
      (error.error_code || error.errorCode) === 'TOKEN_EXPIRED' &&
      retry &&
      !path.startsWith('/auth/')
    ) {
      await refresh();
      return request(path, options, false);
    }
    if ((status === 401 || (status === 403 && !token)) && !path.startsWith('/auth/')) {
      clearTokens();
      window.dispatchEvent(new Event('auth-expired'));
    }
    throw new Error(error.msg || error.message || `请求失败 (${response.status})`);
  }
  return unwrap(payload);
}
export const post = (path: string, body: unknown, method = 'POST') =>
  request(path, { method, body: JSON.stringify(body) });
async function openStream(path: string, options: RequestInit, retry = true): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${sessionStorage.getItem('access_token') || ''}`);
  const response = await fetch(base + path, { ...options, headers });
  if (response.ok && !response.headers.get('content-type')?.includes('application/json'))
    return response;
  const error = await response
    .clone()
    .json()
    .catch(() => ({}));
  const businessError =
    error.code >= 400 && ('msg' in error || 'error_code' in error || 'errorCode' in error);
  if (response.ok && !businessError) return response;
  const status = businessError ? error.code : response.status;
  if (status === 401 && retry && (error.error_code || error.errorCode) === 'TOKEN_EXPIRED') {
    await refresh();
    return openStream(path, options, false);
  }
  if (status === 401) {
    clearTokens();
    window.dispatchEvent(new Event('auth-expired'));
  }
  throw new Error(error.msg || error.message || `连接失败 (${response.status})`);
}
export async function downloadFile(fileId: string, filename: string) {
  const response = await openStream(`/files/${encodeURIComponent(fileId)}/content`, {});
  const blob = await response.blob(),
    url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export async function readChatFile(fileId: string): Promise<Blob> {
  return (await openStream(`/files/${encodeURIComponent(fileId)}/content`, {})).blob();
}
export async function stream(
  path: string,
  body: unknown,
  onText: (chunk: string) => void,
  signal: AbortSignal,
) {
  const response = await openStream(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  });
  if (!response.headers.get('content-type')?.includes('text/event-stream')) {
    const data = unwrap(await response.json());
    onText(data.output || JSON.stringify(data));
    return;
  }
  const reader = response.body!.getReader(),
    decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value, { stream: !done });
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() || '';
    for (const frame of frames) consume(frame);
    if (done) {
      if (buffer.trim()) consume(buffer);
      break;
    }
  }
  function consume(frame: string) {
    const data = frame
      .split(/\r?\n/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n');
    if (!data || data === '[DONE]') return;
    try {
      const parsed = JSON.parse(data);
      if (parsed.output) onText(parsed.output);
      if (parsed.error) throw new Error(parsed.error);
    } catch (e) {
      if (e instanceof SyntaxError) onText(data);
      else throw e;
    }
  }
}
export async function subscribe(onEvent: (event: any) => void, signal: AbortSignal) {
  const response = await openStream('/sse/subscribe', {
    headers: { Accept: 'text/event-stream' },
    signal,
  });
  const reader = response.body!.getReader(),
    decoder = new TextDecoder();
  let buffer = '';
  while (!signal.aborted) {
    const { value, done } = await reader.read();
    if (done) throw new Error('实时连接已断开，请刷新页面重连');
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() || '';
    for (const frame of frames) {
      const text = frame
        .split(/\r?\n/)
        .filter((l) => l.startsWith('data:'))
        .map((l) => l.slice(5).trim())
        .join('\n');
      if (text) {
        try {
          onEvent(JSON.parse(text));
        } catch {
          /* heartbeat */
        }
      }
    }
  }
}
