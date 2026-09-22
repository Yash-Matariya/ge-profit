import { useState } from "react";
import { DecantPage } from "./components/DecantPage";
import { FlipsPage } from "./components/FlipsPage";
import { ProcessingPage } from "./components/ProcessingPage";

type Tab = "flips" | "decant" | "processing";

const TABS: { id: Tab; label: string }[] = [
  { id: "flips", label: "Flipping" },
  { id: "decant", label: "Decanting" },
  { id: "processing", label: "Processing" },
];

export default function App() {
  const [tab, setTab] = useState<Tab>("flips");

  return (
    <div className="min-h-dvh bg-stone-950 text-stone-200">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-amber-600 focus:px-3 focus:py-2 focus:text-stone-950"
      >
        Skip to content
      </a>

      <header className="border-b border-stone-800">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-8 gap-y-3 px-6 py-4">
          <div>
            <h1 className="text-xl font-semibold tracking-tight text-stone-50">GE Profit</h1>
            <p className="text-xs text-stone-500">Grand Exchange market analytics</p>
          </div>

          <nav aria-label="Calculators" className="flex gap-1">
            {TABS.map((t) => (
              <button
                key={t.id}
                onClick={() => setTab(t.id)}
                aria-current={tab === t.id ? "page" : undefined}
                className={`rounded-md px-3 py-1.5 text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-600 ${
                  tab === t.id
                    ? "bg-stone-800 text-stone-50"
                    : "text-stone-400 hover:bg-stone-900 hover:text-stone-200"
                }`}
              >
                {t.label}
              </button>
            ))}
          </nav>

          <p className="ml-auto max-w-xs text-right text-xs text-stone-600">
            Prices from the OSRS Wiki, refreshed server-side
          </p>
        </div>
      </header>

      <main id="main" className="mx-auto max-w-7xl px-6 py-8">
        {tab === "flips" && <FlipsPage />}
        {tab === "decant" && <DecantPage />}
        {tab === "processing" && <ProcessingPage />}
      </main>

      <footer className="border-t border-stone-800">
        <div className="mx-auto max-w-7xl px-6 py-6 text-xs text-stone-600">
          <p>
            Price data from the{" "}
            <a
              href="https://prices.runescape.wiki/"
              className="text-stone-500 underline underline-offset-2 hover:text-stone-300"
              target="_blank"
              rel="noreferrer noopener"
            >
              OSRS Wiki real-time prices API
            </a>
            . Not affiliated with Jagex. Prices move constantly — treat every number as a starting
            point, not a promise.
          </p>
        </div>
      </footer>
    </div>
  );
}
