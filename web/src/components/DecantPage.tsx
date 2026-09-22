import { useMemo } from "react";
import { api, type DecantOpportunity } from "../lib/api";
import { exact, gp } from "../lib/format";
import { useApi, usePolling } from "../lib/useApi";
import { DataTable, type Column } from "./DataTable";

export function DecantPage() {
  const { data, loading, refreshing, error, reload } = useApi(() => api.decant(1, 50), []);
  usePolling(reload, 60_000);

  const columns = useMemo<Column<DecantOpportunity>[]>(
    () => [
      {
        key: "potion",
        header: "Potion",
        render: (d) => <span className="text-stone-100">{d.potion}</span>,
      },
      {
        key: "trade",
        header: "Trade",
        render: (d) => (
          <span className="text-stone-300">
            buy <span className="text-sky-300">({d.buyDoses})</span> → sell{" "}
            <span className="text-emerald-300">({d.sellDoses})</span>
          </span>
        ),
      },
      {
        key: "buyAt",
        header: "Buy at",
        numeric: true,
        render: (d) => <span title={exact(d.buyAt)}>{gp(d.buyAt)}</span>,
      },
      {
        key: "sellAt",
        header: "Sell at",
        numeric: true,
        render: (d) => <span title={exact(d.sellAt)}>{gp(d.sellAt)}</span>,
      },
      {
        key: "costPerDose",
        header: "Cost / dose",
        numeric: true,
        render: (d) => <span className="text-stone-400">{d.costPerDose.toFixed(1)}</span>,
      },
      {
        key: "revenuePerDose",
        header: "Revenue / dose",
        hint: "after tax",
        numeric: true,
        render: (d) => <span className="text-stone-400">{d.revenuePerDose.toFixed(1)}</span>,
      },
      {
        key: "profitPerDose",
        header: "Profit / dose",
        numeric: true,
        render: (d) => <span className="text-emerald-400">{gp(d.profitPerDose)}</span>,
      },
      {
        key: "per100",
        header: "Per 100 doses",
        numeric: true,
        render: (d) => (
          <span className="font-medium text-emerald-300" title={exact(d.profitPerHundredDoses)}>
            {gp(d.profitPerHundredDoses)}
          </span>
        ),
      },
      {
        key: "volume",
        header: "Volume",
        hint: "thinner leg",
        numeric: true,
        render: (d) => <span className="text-stone-400">{exact(d.limitingVolume)}</span>,
      },
    ],
    [],
  );

  return (
    <section>
      <header className="mb-4">
        <h2 className="text-lg font-medium text-stone-100">Decanting</h2>
        <p className="mt-1 max-w-2xl text-sm text-stone-400">
          Potions come in 1, 2, 3 and 4 dose forms of the same drink, and decanting moves freely
          between them. So every form should cost the same{" "}
          <strong className="font-medium text-stone-300">per dose</strong> — and often does not,
          because players buy the convenient 4-dose and dump the awkward leftovers. Everything
          below is priced per dose, which is the only way the forms are comparable.
        </p>
      </header>

      <div className="mb-4 flex items-center justify-between rounded-lg border border-stone-800 bg-stone-900/40 px-4 py-2.5 text-xs text-stone-500">
        <span>Both sides need real volume — a trade you can enter but not exit is not a trade.</span>
        <span>{refreshing ? "Refreshing…" : "Updates every minute"}</span>
      </div>

      <DataTable
        rows={data ?? []}
        columns={columns}
        rowKey={(d) => `${d.buyItemId}-${d.sellItemId}`}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage="No dose mispricing worth trading right now."
      />
    </section>
  );
}
