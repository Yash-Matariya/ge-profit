import { useMemo } from "react";
import { api, type AlchOpportunity, type RecipeMargin } from "../lib/api";
import { exact, gp, percent } from "../lib/format";
import { useApi, usePolling } from "../lib/useApi";
import { DataTable, type Column } from "./DataTable";

export function ProcessingPage() {
  const alch = useApi(() => api.alch(1, 100, 50), []);
  const recipes = useApi(() => api.recipes(1, 50, 50), []);

  usePolling(() => {
    alch.reload();
    recipes.reload();
  }, 60_000);

  const alchColumns = useMemo<Column<AlchOpportunity>[]>(
    () => [
      { key: "name", header: "Item", render: (a) => <span className="text-stone-100">{a.name}</span> },
      {
        key: "buyAt",
        header: "Buy at",
        numeric: true,
        render: (a) => <span title={exact(a.buyAt)}>{gp(a.buyAt)}</span>,
      },
      {
        key: "rune",
        header: "Nature rune",
        numeric: true,
        render: (a) => <span className="text-stone-500">{gp(a.natureRuneCost)}</span>,
      },
      {
        key: "alch",
        header: "Alch value",
        numeric: true,
        render: (a) => <span title={exact(a.highAlchValue)}>{gp(a.highAlchValue)}</span>,
      },
      {
        key: "profit",
        header: "Profit each",
        numeric: true,
        render: (a) => <span className="text-emerald-400">{gp(a.profitPerItem)}</span>,
      },
      {
        key: "hourly",
        header: "Per hour",
        hint: "~1200 casts",
        numeric: true,
        render: (a) => (
          <span className="font-medium text-emerald-300" title={exact(a.profitPerHour)}>
            {gp(a.profitPerHour)}
          </span>
        ),
      },
      {
        key: "volume",
        header: "Volume",
        numeric: true,
        render: (a) => <span className="text-stone-400">{exact(a.volume)}</span>,
      },
    ],
    [],
  );

  const recipeColumns = useMemo<Column<RecipeMargin>[]>(
    () => [
      {
        key: "recipe",
        header: "Recipe",
        render: (r) => (
          <div>
            <div className="text-stone-100">{r.recipe}</div>
            <div className="text-xs text-stone-500">
              {r.inputs.map((i) => `${i.quantity}x ${i.name}`).join(" + ")} → {r.outputName}
            </div>
          </div>
        ),
      },
      {
        key: "skill",
        header: "Skill",
        render: (r) => <span className="text-stone-400">{r.skill}</span>,
      },
      {
        key: "cost",
        header: "Inputs cost",
        numeric: true,
        render: (r) => <span title={exact(r.inputCost)}>{gp(r.inputCost)}</span>,
      },
      {
        key: "revenue",
        header: "Output value",
        hint: "after tax",
        numeric: true,
        render: (r) => <span title={exact(r.revenueAfterTax)}>{gp(r.revenueAfterTax)}</span>,
      },
      {
        key: "profit",
        header: "Profit",
        numeric: true,
        render: (r) => (
          <span className="font-medium text-emerald-300">{gp(r.profit)}</span>
        ),
      },
      {
        key: "roi",
        header: "ROI",
        numeric: true,
        render: (r) => <span className="text-stone-400">{percent(r.roiPercent)}</span>,
      },
      {
        key: "volume",
        header: "Output volume",
        numeric: true,
        render: (r) => <span className="text-stone-400">{exact(r.outputVolume)}</span>,
      },
    ],
    [],
  );

  return (
    <section className="space-y-10">
      <div>
        <header className="mb-4">
          <h2 className="text-lg font-medium text-stone-100">High alchemy</h2>
          <p className="mt-1 max-w-2xl text-sm text-stone-400">
            Buy an item, cast High Level Alchemy, keep the difference less the nature rune.{" "}
            <strong className="font-medium text-stone-300">No GE tax applies</strong> — alching
            sells to the game, not to another player, which is what makes it competitive with
            flipping on some items.
          </p>
        </header>
        <DataTable
          rows={alch.data ?? []}
          columns={alchColumns}
          rowKey={(a) => a.itemId}
          loading={alch.loading}
          error={alch.error}
          onRetry={alch.reload}
          emptyMessage="Nothing is profitable to alch at current prices."
        />
      </div>

      <div>
        <header className="mb-4">
          <h2 className="text-lg font-medium text-stone-100">Recipes</h2>
          <p className="mt-1 max-w-2xl text-sm text-stone-400">
            Conversions worth doing: the inputs are valued at what you would pay for them, and the
            output at what you would receive after tax. A recipe whose inputs are not all currently
            priced is left out rather than treated as free.
          </p>
        </header>
        <DataTable
          rows={recipes.data ?? []}
          columns={recipeColumns}
          rowKey={(r) => r.recipe}
          loading={recipes.loading}
          error={recipes.error}
          onRetry={recipes.reload}
          emptyMessage="No recipe is profitable at current prices."
        />
      </div>
    </section>
  );
}
