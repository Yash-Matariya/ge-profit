/**
 * Coin formatting.
 *
 * OSRS players read large numbers in k / m / b, and a table of raw digits is
 * genuinely harder to scan. The full number stays available as a tooltip, so
 * nothing is lost -- this is presentation, never used in a calculation.
 */
export function gp(value: number): string {
  const sign = value < 0 ? "-" : "";
  const n = Math.abs(value);

  if (n >= 1_000_000_000) return `${sign}${trim(n / 1_000_000_000)}b`;
  if (n >= 1_000_000) return `${sign}${trim(n / 1_000_000)}m`;
  if (n >= 1_000) return `${sign}${trim(n / 1_000)}k`;
  return `${sign}${n}`;
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
