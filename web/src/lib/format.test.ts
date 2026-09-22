import { describe, expect, it } from "vitest";
import { ago, exact, gp, percent } from "./format";

describe("gp", () => {
  it("leaves small numbers alone", () => {
    expect(gp(0)).toBe("0");
    expect(gp(999)).toBe("999");
  });

  it("abbreviates thousands, millions and billions", () => {
    expect(gp(1_000)).toBe("1k");
    expect(gp(1_500)).toBe("1.5k");
    expect(gp(1_000_000)).toBe("1m");
    expect(gp(2_400_000)).toBe("2.4m");
    expect(gp(3_000_000_000)).toBe("3b");
  });

  it("drops a trailing .0 rather than printing 2.0m", () => {
    expect(gp(2_000_000)).toBe("2m");
    expect(gp(2_000)).toBe("2k");
  });

  it("keeps the sign on a loss", () => {
    // A negative margin is a real answer and must not be swallowed.
    expect(gp(-1_500)).toBe("-1.5k");
    expect(gp(-50)).toBe("-50");
  });

  it("promotes at the boundary instead of printing 1000k", () => {
    // 999,999 is 999.999k, which rounds to 1000.0k -- it should read as 1m.
    expect(gp(999_999)).toBe("1m");
    expect(gp(1_000_000)).toBe("1m");
    expect(gp(999_499)).toBe("999.5k");
    expect(gp(999_999_999)).toBe("1b");
  });
});

describe("exact", () => {
  it("groups digits so long numbers are readable", () => {
    expect(exact(1234567)).toBe("1,234,567");
  });
});

describe("percent", () => {
  it("shows one decimal", () => {
    expect(percent(12.34)).toBe("12.3%");
    expect(percent(0)).toBe("0.0%");
  });
});

describe("ago", () => {
  it("describes how stale a price is", () => {
    const now = Date.now();
    expect(ago(new Date(now - 10_000).toISOString())).toBe("just now");
    expect(ago(new Date(now - 5 * 60_000).toISOString())).toBe("5m ago");
    expect(ago(new Date(now - 3 * 3_600_000).toISOString())).toBe("3h ago");
    expect(ago(new Date(now - 2 * 86_400_000).toISOString())).toBe("2d ago");
  });

  it("does not report a future timestamp as negative", () => {
    // Clock skew between the server and the browser is normal.
    expect(ago(new Date(Date.now() + 60_000).toISOString())).toBe("just now");
  });
});
