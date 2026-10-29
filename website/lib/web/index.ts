import type { Backend } from "./backend";
import { allowLocal, cloudConfigured } from "./config";

let pending: Promise<Backend | null> | null = null;

/**
 * The backend for this browser: Supabase (auth and data) when configured, the local test backend in
 * development, otherwise null (the UI explains that the site isn't connected yet).
 */
export function getBackend(): Promise<Backend | null> {
  if (!pending) {
    pending = (async () => {
      if (cloudConfigured) return (await import("./cloud")).createCloudBackend();
      if (allowLocal) return (await import("./local")).createLocalBackend(window.localStorage);
      return null;
    })();
  }
  return pending;
}
