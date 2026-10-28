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
export const IS_DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true';
export const API_BASE_URL = (import.meta.env.VITE_API_BASE || '/api/v1').replace(/\/$/, '');
const REQUEST_TIMEOUT_MS = 30000;
const AUTH_TIMEOUT_MS = 20000;
let refreshFlight: Promise<void> | null = null;
export function tokens(data: { access_token: string; refresh_token: string }) {
  sessionStorage.setItem('access_token', data.access_token);
  sessionStorage.setItem('refresh_token', data.refresh_token);
}
export function clearTokens() {
  sessionStorage.removeItem('access_token');
  sessionStorage.removeItem('refresh_token');
}
export function unwrap<T = unknown>(data: unknown): T {
  if (
    data &&
    typeof data === 'object' &&
    'code' in data &&
    typeof data.code === 'number' &&
    ('msg' in data || 'data' in data)
  ) {
    if (data.code >= 400) throw new Error(String(('msg' in data && data.msg) || '请求失败'));
    return ('data' in data ? data.data : undefined) as T;
  }
  return data as T;
}
async function refresh() {
  if (!refreshFlight)
    refreshFlight = (async () => {
      const response = await fetch(API_BASE_URL + '/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${sessionStorage.getItem('access_token') || ''}`,
        },
        body: JSON.stringify({ refresh_token: sessionStorage.getItem('refresh_token') }),
        signal: AbortSignal.timeout(AUTH_TIMEOUT_MS),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload.code >= 400 || !(payload.data || payload).access_token) {
        clearTokens();
        window.dispatchEvent(new Event('auth-expired'));
        throw new Error('登录已过期，请重新登录');
      }
      tokens(unwrap<{ access_token: string; refresh_token: string }>(payload));
    })().finally(() => {
      refreshFlight = null;
    });
  return refreshFlight;
}
export async function request<T = unknown>(
  path: string,
  options: RequestInit = {},
  retry = true,
): Promise<T> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const token = sessionStorage.getItem('access_token');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(API_BASE_URL + path, {
    ...options,
    headers,
    signal: options.signal || AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (response.status === 204) return null as T;
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
      return request<T>(path, options, false);
    }
    if ((status === 401 || (status === 403 && !token)) && !path.startsWith('/auth/')) {
      clearTokens();
      window.dispatchEvent(new Event('auth-expired'));
    }
    throw new Error(error.msg || error.message || `请求失败 (${response.status})`);
  }
  return unwrap<T>(payload);
}
export const post = <T = unknown>(path: string, body: unknown, method = 'POST') =>
  request<T>(path, { method, body: JSON.stringify(body) });
async function openStream(path: string, options: RequestInit, retry = true): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${sessionStorage.getItem('access_token') || ''}`);
  const response = await fetch(API_BASE_URL + path, { ...options, headers });
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
    const data = unwrap<{ output?: string; content?: string }>(await response.json());
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
export interface RealtimeNotification {
  type: string;
  payload?: unknown;
}
export interface SubscriptionFailure {
  stage: 'parse' | 'handler';
  error: unknown;
  eventType?: string;
}
const subscriptionDiagnostics = {
  connectionAttempts: 0,
  reconnectAttempts: 0,
  handshakes: 0,
  parseFailures: 0,
  handlerFailures: 0,
  activeHandlers: 0,
  maxConcurrentHandlers: 0,
};
/** 当前页面模块的累计诊断计数，用户及会话编号不作为指标标签。 */
export function getSubscriptionDiagnostics() {
  return Object.freeze({ ...subscriptionDiagnostics });
}
export function recordSubscriptionAttempt(reconnect: boolean) {
  subscriptionDiagnostics.connectionAttempts++;
  if (reconnect) subscriptionDiagnostics.reconnectAttempts++;
}
/** 业务处理与网络回调分离时，也记录其失败阶段。 */
export function recordSubscriptionFailure(stage: SubscriptionFailure['stage']) {
  if (stage === 'parse') subscriptionDiagnostics.parseFailures++;
  else subscriptionDiagnostics.handlerFailures++;
}
export async function subscribe(
  onEvent: (event: RealtimeNotification) => void | Promise<void>,
  signal: AbortSignal,
  onFailure: (failure: SubscriptionFailure) => void = () => undefined,
) {
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
      if (!text) continue; // SSE comment frames carry heartbeat traffic without JSON data.
      let event: RealtimeNotification;
      try {
        const parsed = JSON.parse(text);
        if (
          !parsed ||
          typeof parsed !== 'object' ||
          typeof parsed.type !== 'string' ||
          !parsed.type
        )
          throw new Error('无效的实时事件结构');
        event = parsed;
      } catch (error) {
        report({ stage: 'parse', error });
        continue;
      }
      if (event.type === 'CONNECTION_ESTABLISHED') subscriptionDiagnostics.handshakes++;
      subscriptionDiagnostics.activeHandlers++;
      subscriptionDiagnostics.maxConcurrentHandlers = Math.max(
        subscriptionDiagnostics.maxConcurrentHandlers,
        subscriptionDiagnostics.activeHandlers,
      );
      try {
        // 业务处理自行控制并发，耗时回调不能阻塞后续网络数据读取。
        const result = onEvent(event);
        if (result && typeof result.then === 'function') {
          Promise.resolve(result).then(
            () => {
              subscriptionDiagnostics.activeHandlers--;
            },
            (error) => {
              subscriptionDiagnostics.activeHandlers--;
              report({ stage: 'handler', error, eventType: event.type });
            },
          );
        } else subscriptionDiagnostics.activeHandlers--;
      } catch (error) {
        subscriptionDiagnostics.activeHandlers--;
        report({ stage: 'handler', error, eventType: event.type });
      }
    }
  }
  function report(failure: SubscriptionFailure) {
    recordSubscriptionFailure(failure.stage);
    try {
      onFailure(failure);
    } catch {
      {
        /* 诊断状态由计数器和页面提示提供，不写入控制台。 */
      }
    }
  }
}
