export const HANDLE_MAX = 15;

/** Readable @handle for a new profile, built the same way as the Android app. */
export function handleFromAccount(email: string | null | undefined, name: string | null | undefined): string {
  let base: string | null = null;
  if (email && email.includes("@")) base = email.slice(0, email.indexOf("@"));
  if (!base || !base.trim()) base = name ?? null;
  let cleaned = (base ?? "").toLowerCase().replace(/[^a-z0-9_]/g, "");
  if (!cleaned) cleaned = "member";
  if (cleaned.length > HANDLE_MAX) cleaned = cleaned.slice(0, HANDLE_MAX);
  return "@" + cleaned;
}
