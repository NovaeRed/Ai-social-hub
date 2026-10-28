export interface RefreshDiagnostics {
  requests: number;
  runs: number;
  coalescedRequests: number;
  failures: number;
  running: boolean;
  pending: boolean;
}
export interface CoalescedRefresh {
  (): Promise<void>;
  diagnostics(): Readonly<RefreshDiagnostics>;
}
export function createCoalescedRefresh(task: () => Promise<void>): CoalescedRefresh {
  let flight: Promise<void> | null = null;
  let isRefreshRequested = false;
  const counts = { requests: 0, runs: 0, coalescedRequests: 0, failures: 0 };
  const refresh = () => {
    counts.requests++;
    if (flight) counts.coalescedRequests++;
    isRefreshRequested = true;
    if (!flight) {
      flight = Promise.resolve().then(async () => {
        try {
          while (isRefreshRequested) {
            isRefreshRequested = false;
            counts.runs++;
            await task();
          }
        } catch (error) {
          counts.failures++;
          throw error;
        } finally {
          // 在结束循环的同一微任务中清理；额外的 finally 会留下丢失刷新触发的时间窗口。
          flight = null;
          isRefreshRequested = false;
        }
      });
    }
    return flight;
  };
  return Object.assign(refresh, {
    diagnostics: () =>
      Object.freeze({ ...counts, running: flight !== null, pending: isRefreshRequested }),
  });
}
