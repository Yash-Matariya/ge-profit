import { useMemo, useState } from "react";
import { api, type Flip, type FlipFilters } from "../lib/api";
import { ago, exact, gp, percent } from "../lib/format";
import { useApi, usePolling } from "../lib/useApi";
import { DataTable, type Column } from "./DataTable";

const SORTS: { value: FlipFilters["sort"]; label: string }[] = [
  { value: "POTENTIAL_PROFIT", label: "Potential profit" },
  { value: "MARGIN", label: "Margin" },
  { value: "ROI", label: "ROI" },
  { value: "VOLUME", label: "Volume" },
];

export function FlipsPage() {
  const [filters, setFilters] = useState<FlipFilters>({
    minMargin: 1,
    minVolume: 100,
    maxBuyPrice: 0,
    sort: "POTENTIAL_PROFIT",
    limit: 100,
  });

  const { data, loading, refreshing, error, reload } = useApi(
    () => api.flips(filters),
    [filters.minMargin, filters.minVolume, filters.maxBuyPrice, filters.sort, filters.limit],
  );

  // Prices refresh server-side every minute, so there is no point asking faster.
  usePolling(reload, 60_000);

  const columns = useMemo<Column<Flip>[]>(
    () => [
      {
        key: "name",
        header: "Item",
        render: (f) => (
          <span className="flex items-center gap-2">
            <span className="text-stone-100">{f.name}</span>
            {f.members && (
              <span
                title="Members only"
                className="rounded bg-amber-900/50 px-1.5 py-0.5 text-[10px] font-medium text-amber-300"
              >
                P2P
              </span>
            )}
          </span>
        ),
      },
      {
        key: "buy",
        header: "Buy at",
        numeric: true,
        render: (f) => <span title={exact(f.buyAt)}>{gp(f.buyAt)}</span>,
      },
      {
        key: "sell",
        header: "Sell at",
        numeric: true,
        render: (f) => <span title={exact(f.sellAt)}>{gp(f.sellAt)}</span>,
      },
      {
        key: "tax",
        header: "Tax",
        numeric: true,
        render: (f) => <span className="text-stone-500">{gp(f.tax)}</span>,
      },
      {
        key: "margin",
        header: "Margin",
        hint: "after tax",
        numeric: true,
        render: (f) => (
          <span className={f.margin > 0 ? "text-emerald-400" : "text-rose-400"}>
            {gp(f.margin)}
          </span>
        ),
      },
      {
        key: "roi",
        header: "ROI",
        numeric: true,
        render: (f) => <span className="text-stone-400">{percent(f.roiPercent)}</span>,
      },
      {
        key: "limit",
        header: "Limit",
        hint: "per 4h",
        numeric: true,
        render: (f) => <span className="text-stone-400">{exact(f.buyLimit)}</span>,
      },
      {
        key: "potential",
        header: "Potential",
        hint: "margin x limit",
        numeric: true,
        render: (f) => (
          <span className="font-medium text-emerald-300" title={exact(f.potentialProfit)}>
            {gp(f.potentialProfit)}
          </span>
        ),
      },
      {
        key: "volume",
        header: "Volume",
        hint: "last 5m",
        numeric: true,
        render: (f) => <span className="text-stone-400">{exact(f.volume)}</span>,
      },
      {
        key: "age",
        header: "Priced",
        numeric: true,
        render: (f) => <span className="text-stone-500">{ago(f.pricedAt)}</span>,
      },
    ],
    [],
  );

  return (
    <section>
      <header className="mb-4">
        <h2 className="text-lg font-medium text-stone-100">Flipping</h2>
        <p className="mt-1 max-w-2xl text-sm text-stone-400">
          Buy at the low price, sell at the high one, keep the difference less tax. Sorted by{" "}
          <strong className="font-medium text-stone-300">potential profit</strong> by default —
          margin multiplied by the buy limit — because a big margin on an item you can only buy
          eight of is worth less than a small one you can buy thousands of.
        </p>
      </header>

      <div className="mb-4 flex flex-wrap items-end gap-4 rounded-lg border border-stone-800 bg-stone-900/40 p-4">
        <NumberField
          label="Min margin"
          value={filters.minMargin}
          onChange={(v) => setFilters((f) => ({ ...f, minMargin: v }))}
        />
        <NumberField
          label="Min volume"
          value={filters.minVolume}
          onChange={(v) => setFilters((f) => ({ ...f, minVolume: v }))}
        />
        <NumberField
          label="Max buy price"
          hint="0 = no cap"
          value={filters.maxBuyPrice}
          onChange={(v) => setFilters((f) => ({ ...f, maxBuyPrice: v }))}
        />
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-stone-400">Sort by</span>
          <select
            value={filters.sort}
            onChange={(e) =>
              setFilters((f) => ({ ...f, sort: e.target.value as FlipFilters["sort"] }))
            }
            className="w-40 rounded-md border border-stone-700 bg-stone-950 px-2 py-1.5 text-sm text-stone-100 focus:border-amber-600 focus:outline-none focus-visible:ring-1 focus-visible:ring-amber-600"
          >
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </label>

        <span className="ml-auto text-xs text-stone-500">
          {refreshing ? "Refreshing…" : "Updates every minute"}
        </span>
      </div>

      <DataTable
        rows={data ?? []}
        columns={columns}
        rowKey={(f) => f.itemId}
        loading={loading}
        error={error}
        onRetry={reload}
      />
    </section>
  );
}

function NumberField({
  label,
  value,
  onChange,
  hint,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  hint?: string;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-xs font-medium text-stone-400">
        {label}
        {hint && <span className="ml-1 font-normal text-stone-600">{hint}</span>}
      </span>
      <input
        type="number"
        min={0}
        value={value}
        // An empty input parses as NaN, which would be sent to the API as
        // "NaN" and rejected. Treat it as zero.
        onChange={(e) => onChange(Number(e.target.value) || 0)}
        className="w-32 rounded-md border border-stone-700 bg-stone-950 px-2 py-1.5 text-sm tabular-nums text-stone-100 focus:border-amber-600 focus:outline-none focus-visible:ring-1 focus-visible:ring-amber-600"
      />
    </label>
  );
}
