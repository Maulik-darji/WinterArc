/** Public build-time settings. Next inlines these only when written as literal process.env.NEXT_PUBLIC_*. */
export const supabaseConfig = {
  url: process.env.NEXT_PUBLIC_SUPABASE_URL ?? "",
  key: process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? "",
};

export const cloudConfigured = Boolean(supabaseConfig.url && supabaseConfig.key);

/** The browser-only test backend: always in development, opt-in in production. */
export const allowLocal =
  process.env.NODE_ENV !== "production" || process.env.NEXT_PUBLIC_ALLOW_LOCAL === "true";

export const APP_DOWNLOAD_URL = "/downloads/WinterArc-1.0.apk";
