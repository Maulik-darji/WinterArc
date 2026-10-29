/** Input rules shared with the Android app (domain/feed/FeedRules). */
export const POST_MAX = 280;
export const COMMENT_MAX = 500;
/** Replies deeper than this are shown at this indent with "Replying to @handle". */
export const MAX_VISUAL_DEPTH = 3;
export const NAME_MAX = 50;
export const BIO_MAX = 160;

const length = (text: string) => Array.from(text).length;

/** Trims and collapses runs of 3+ blank lines; null when empty or over the limit. */
export function clean(text: string | null | undefined, max: number): string | null {
  if (text == null) return null;
  const t = text.trim().replace(/\n{3,}/g, "\n\n");
  if (t.length === 0 || length(t) > max) return null;
  return t;
}

/** Characters left for a live counter (negative when over the limit). */
export function remaining(text: string | null | undefined, max: number): number {
  return max - length((text ?? "").trim());
}
