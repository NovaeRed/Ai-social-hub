import { onUnmounted, type Ref } from 'vue';
import {
  IS_DEMO_MODE,
  subscribe,
  recordSubscriptionAttempt,
  recordSubscriptionFailure,
  type SubscriptionFailure,
} from '../services/http';
const MAX_RECONNECT_DELAY_MS = 30000;
export function useRealtimeConnection(
  refreshRealtimeState: () => Promise<void>,
  realtime: Ref<string>,
  notify: (text: string) => void,
) {
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let reconnectAttempt = 0,
    connectEpoch = 0;

  let sseController: AbortController | undefined;
  let realtimeRefreshFlight: Promise<void> | null = null;
  function reportRealtimeFailure(failure: SubscriptionFailure) {
    if (failure.stage === 'handler') notify('实时状态更新失败，请刷新页面重试');
  }
  function connect() {
    if (IS_DEMO_MODE || !sessionStorage.getItem('access_token')) return;
    recordSubscriptionAttempt(reconnectAttempt > 0);
    const epoch = ++connectEpoch;
    clearTimeout(reconnectTimer);
    sseController?.abort();
    sseController = new AbortController();
    realtime.value = '连接中';
    subscribe(
      (e) => {
        if (epoch !== connectEpoch) return;
        if (e.type === 'CONNECTION_ESTABLISHED') {
          realtime.value = '实时在线';
          reconnectAttempt = 0;
        }
        if (['MESSAGE_CREATED', 'AI_TASK_COMPLETED', 'CONNECTION_ESTABLISHED'].includes(e.type)) {
          const flight = refreshRealtimeState();
          // 合并的刷新请求只挂一个失败观察器，避免突发事件重复提示。
          if (realtimeRefreshFlight !== flight) {
            realtimeRefreshFlight = flight;
            void flight.then(
              () => {},
              (error) => {
                recordSubscriptionFailure('handler');
                if (epoch === connectEpoch) reportRealtimeFailure({ stage: 'handler', error });
              },
            );
          }
        }
      },
      sseController.signal,
      (failure) => {
        if (epoch === connectEpoch) reportRealtimeFailure(failure);
      },
    ).catch((e) => {
      if (
        e.name !== 'AbortError' &&
        epoch === connectEpoch &&
        sessionStorage.getItem('access_token')
      ) {
        realtime.value = '正在重连';

        const delay = Math.min(MAX_RECONNECT_DELAY_MS, 1000 * 2 ** Math.min(reconnectAttempt++, 5));
        reconnectTimer = setTimeout(connect, delay * (0.5 + Math.random() * 0.5));
      }
    });
  }

  function disconnect() {
    reconnectAttempt = 0;
    connectEpoch++;
    clearTimeout(reconnectTimer);
    sseController?.abort();
  }
  onUnmounted(disconnect);
  return { connect, disconnect };
}
