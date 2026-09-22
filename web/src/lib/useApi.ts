import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Load data, with the three states every screen needs: loading, error, data.
 *
 * Two details that matter more than they look:
 *
 * - **Stale responses are discarded.** Change a filter twice quickly and two
 *   requests are in flight; without a guard the slower one can land last and
 *   overwrite the newer result with older data. Each call takes a sequence
 *   number and only the newest is allowed to set state.
 * - **A refresh keeps the previous rows on screen.** Blanking the table every
 *   sixty seconds makes a live dashboard flicker and unreadable.
 */
export function useApi<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const sequence = useRef(0);
  // Keep the latest loader in a ref so the effect does not re-run just because
  // the caller passed a new closure on every render.
  const loadRef = useRef(load);
  loadRef.current = load;

  const run = useCallback(async () => {
    const mine = ++sequence.current;
    setLoading(true);
    try {
      const result = await loadRef.current();
      if (mine === sequence.current) {
        setData(result);
        setError(null);
      }
    } catch (e) {
      if (mine === sequence.current) {
        setError(e instanceof Error ? e.message : "Something went wrong.");
      }
    } finally {
      if (mine === sequence.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return {
    data,
    // Only show the skeleton on the first load; later refreshes keep the rows.
    loading: loading && data === null,
    refreshing: loading && data !== null,
    error,
    reload: run,
  };
}

/** Re-run something on an interval, and stop when the tab is hidden. */
export function usePolling(fn: () => void, intervalMs: number) {
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    // Polling a background tab burns the user's battery and our upstream quota
    // for data nobody is looking at.
    const tick = () => {
      if (!document.hidden) {
        fnRef.current();
      }
    };
    const id = window.setInterval(tick, intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
}
