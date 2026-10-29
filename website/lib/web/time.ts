/** "now", "5m", "3h", "4d", then "Oct 4" (this year) or "Oct 4, 2025". */
export function relativeTime(iso: string, now: Date = new Date(), locale?: string): string {
  const then = new Date(iso);
  const ms = now.getTime() - then.getTime();
  const minutes = Math.floor(ms / 60_000);
  if (minutes < 1) return "now";
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d`;
  const sameYear = then.getFullYear() === now.getFullYear();
  return new Intl.DateTimeFormat(locale, sameYear
    ? { month: "short", day: "numeric" }
    : { month: "short", day: "numeric", year: "numeric" }).format(then);
}

/** "3:41 PM · Oct 24, 2026" */
export function exactTime(iso: string, locale?: string): string {
  const d = new Date(iso);
  const time = new Intl.DateTimeFormat(locale, { hour: "numeric", minute: "2-digit" }).format(d);
  const date = new Intl.DateTimeFormat(locale, { month: "short", day: "numeric", year: "numeric" }).format(d);
  return `${time} · ${date}`;
}

/** Empty for zero (X-style), otherwise compact: 7, 1.2K, 3.4M. */
export function compactCount(n: number): string {
  if (n <= 0) return "";
  if (n < 1000) return String(n);
  const f = (v: number) => (Math.round(v * 10) / 10).toString();
  return n < 1_000_000 ? `${f(n / 1000)}K` : `${f(n / 1_000_000)}M`;
}
