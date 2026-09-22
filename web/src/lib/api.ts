/**
 * The one place that talks to the backend.
 *
 * Every fetch goes through `get`, so the error handling, the JSON parsing and
 * the base URL are defined once. A component that needs data calls a named
 * function here and receives typed values -- it never builds a URL itself.
 */

const BASE = import.meta.env.VITE_API_URL ?? "";

export type Flip = {
  itemId: number;
  name: string;
  buyAt: number;
  sellAt: number;
  tax: number;
  margin: number;
  roiPercent: number;
  buyLimit: number;
  potentialProfit: number;
  volume: number;
  members: boolean;
  pricedAt: string;
};

export type DecantOpportunity = {
  potion: string;
  buyItemId: number;
  buyName: string;
  buyDoses: number;
  buyAt: number;
  sellItemId: number;
  sellName: string;
  sellDoses: number;
  sellAt: number;
  taxPerUnitSold: number;
  costPerDose: number;
  revenuePerDose: number;
  profitPerDose: number;
  profitPerHundredDoses: number;
  limitingVolume: number;
};

export type AlchOpportunity = {
  itemId: number;
  name: string;
  buyAt: number;
  highAlchValue: number;
  natureRuneCost: number;
  profitPerItem: number;
  profitPerHour: number;
  volume: number;
  buyLimit: number;
};

export type Ingredient = { itemId: number; name: string; quantity: number };

export type RecipeMargin = {
  recipe: string;
  skill: string;
  inputs: Ingredient[];
  outputItemId: number;
  outputName: string;
  outputQuantity: number;
  inputCost: number;
  revenueAfterTax: number;
  profit: number;
  roiPercent: number;
  outputVolume: number;
};

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function get<T>(path: string, params: Record<string, string | number | boolean> = {}): Promise<T> {
  const query = new URLSearchParams(
    Object.entries(params).map(([k, v]) => [k, String(v)]),
  ).toString();

  const res = await fetch(`${BASE}${path}${query ? `?${query}` : ""}`);

  if (!res.ok) {
    // The backend returns {status, message, at} for handled errors. Fall back
    // to the status text when it is something else entirely, e.g. a proxy.
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body?.message) message = body.message;
    } catch {
      // Body was not JSON; the status text is the best we have.
    }
    throw new ApiError(message, res.status);
  }

  return res.json() as Promise<T>;
}

export type FlipFilters = {
  minMargin: number;
  minVolume: number;
  maxBuyPrice: number;
  sort: "POTENTIAL_PROFIT" | "MARGIN" | "ROI" | "VOLUME";
  limit: number;
};

export const api = {
  flips: (f: FlipFilters) => get<Flip[]>("/api/flips", { ...f }),
  decant: (minProfitPerDose = 1, limit = 50) =>
    get<DecantOpportunity[]>("/api/decant", { minProfitPerDose, limit }),
  alch: (minProfit = 1, minVolume = 100, limit = 50) =>
    get<AlchOpportunity[]>("/api/alch", { minProfit, minVolume, limit }),
  recipes: (minProfit = 1, minVolume = 50, limit = 50) =>
    get<RecipeMargin[]>("/api/recipes", { minProfit, minVolume, limit }),
};
