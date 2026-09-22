import type { ReactNode } from "react";

/**
 * One table component for all four calculators.
 *
 * Each page differs only in its columns, so the loading, empty and error
 * states are defined once here instead of four slightly-different times. Those
 * three states are the ones that get skipped in a demo and noticed immediately
 * in real use.
 */

export type Column<T> = {
  key: string;
  header: string;
  /** Right-align numbers, left-align text -- a column of digits is unreadable ragged. */
  numeric?: boolean;
  render: (row: T) => ReactNode;
  /** Shown under the header, for columns whose meaning is not obvious. */
  hint?: string;
};

type Props<T> = {
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T) => string | number;
  loading?: boolean;
  error?: string | null;
  emptyMessage?: string;
  onRetry?: () => void;
};

export function DataTable<T>({
  rows,
  columns,
  rowKey,
  loading,
  error,
  emptyMessage = "Nothing matches those filters.",
  onRetry,
}: Props<T>) {
  if (error) {
    return (
      <div className="rounded-lg border border-rose-900/60 bg-rose-950/40 p-6 text-center">
        <p className="text-rose-200">{error}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="mt-3 rounded-md border border-rose-700 px-3 py-1.5 text-sm text-rose-100 transition hover:bg-rose-900/60 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-400"
          >
            Try again
          </button>
        )}
      </div>
    );
  }

  // A skeleton rather than a spinner: it holds the layout, so the page does not
  // jump when the data lands.
  if (loading) {
    return (
      <div className="overflow-hidden rounded-lg border border-stone-800">
        <div className="h-11 bg-stone-900" />
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="flex gap-4 border-t border-stone-800/70 px-4 py-3">
            {columns.map((c) => (
              <div
                key={c.key}
                className="h-4 flex-1 animate-pulse rounded bg-stone-800"
                style={{ animationDelay: `${i * 60}ms` }}
              />
            ))}
          </div>
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-stone-800 bg-stone-900/40 p-10 text-center">
        <p className="text-stone-400">{emptyMessage}</p>
        <p className="mt-1 text-sm text-stone-500">
          Try lowering the minimum margin or the minimum volume.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-stone-800">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="bg-stone-900 text-stone-300">
            {columns.map((c) => (
              <th
                key={c.key}
                scope="col"
                className={`px-4 py-2.5 font-medium ${c.numeric ? "text-right" : "text-left"}`}
              >
                {c.header}
                {c.hint && (
                  <span className="block text-xs font-normal text-stone-500">{c.hint}</span>
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              className="border-t border-stone-800/70 transition-colors hover:bg-stone-900/60"
            >
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={`px-4 py-2.5 ${c.numeric ? "text-right tabular-nums" : "text-left"}`}
                >
                  {c.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
