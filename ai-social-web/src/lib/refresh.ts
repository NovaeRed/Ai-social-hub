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
  let requested = false;
  const counts = { requests: 0, runs: 0, coalescedRequests: 0, failures: 0 };
  const refresh = () => {
    counts.requests++;
    if (flight) counts.coalescedRequests++;
    requested = true;
    if (!flight) {
      flight = Promise.resolve().then(async () => {
        try {
          while (requested) {
            requested = false;
            counts.runs++;
            await task();
          }
        } catch (error) {
          counts.failures++;
          throw error;
        } finally {
          // Clear the flight in the same continuation that finishes the loop. A separate
          // Promise.finally would leave a microtask window in which a trigger can be lost.
          flight = null;
          requested = false;
        }
      });
    }
    return flight;
  };
  return Object.assign(refresh, {
    diagnostics: () => Object.freeze({ ...counts, running: flight !== null, pending: requested }),
  });
}
