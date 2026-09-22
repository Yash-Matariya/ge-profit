/**
 * Coin formatting.
 *
 * OSRS players read large numbers in k / m / b, and a table of raw digits is
 * genuinely harder to scan. The full number stays available as a tooltip, so
 * nothing is lost -- this is presentation, never used in a calculation.
 */
const UNITS = [
  { threshold: 1_000_000_000, suffix: "b" },
  { threshold: 1_000_000, suffix: "m" },
  { threshold: 1_000, suffix: "k" },
] as const;

export function gp(value: number): string {
  const sign = value < 0 ? "-" : "";
  const n = Math.abs(value);

  // UNITS runs largest first, so this is the biggest unit the number fills.
  let unit = UNITS.findIndex((u) => n >= u.threshold);
  if (unit === -1) {
    return `${sign}${n}`;
  }

  let scaled = n / UNITS[unit].threshold;
  // Rounding to one decimal can reach 1000: 999,999 is 999.999k, which would
  // print as "1000k". Step up to the larger unit so it reads as "1m".
  if (Number(scaled.toFixed(1)) >= 1000 && unit > 0) {
    unit -= 1;
    scaled = n / UNITS[unit].threshold;
  }

  return `${sign}${trim(scaled)}${UNITS[unit].suffix}`;
}

function trim(n: number): string {
  // One decimal, but not a trailing ".0" -- "1.5m" and "2m", never "2.0m".
  const s = n.toFixed(1);
  return s.endsWith(".0") ? s.slice(0, -2) : s;
}

export function exact(value: number): string {
  return value.toLocaleString("en-GB");
}

export function percent(value: number): string {
  return `${value.toFixed(1)}%`;
}

/** "3 minutes ago" -- how stale the price is matters more than when it was. */
export function ago(iso: string): string {
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}
