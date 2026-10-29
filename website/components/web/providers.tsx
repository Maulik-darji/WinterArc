"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { FEED_MESSAGES, AuthError, FeedError, type Backend } from "@/lib/web/backend";
import { getBackend } from "@/lib/web";
import type { AuthUser, Profile } from "@/lib/web/types";

type Status = "loading" | "ready" | "unavailable";

type Ctx = {
  status: Status;
  backend: Backend | null;
  user: AuthUser | null;
  /** The signed-in user's profile (null until it has been created). */
  me: Profile | null;
  toast: (message: string) => void;
  /** Runs `fn`, turning feed errors into a toast. Returns true on success. */
  run: (fn: () => Promise<unknown>) => Promise<boolean>;
  /** Sends signed-out visitors to the login page; returns true when signed in. */
  requireAuth: () => boolean;
};

const WebContext = createContext<Ctx | null>(null);

export function useWeb(): Ctx {
  const ctx = useContext(WebContext);
  if (!ctx) throw new Error("useWeb outside WebProvider");
  return ctx;
}

export function WebProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [status, setStatus] = useState<Status>("loading");
  const [backend, setBackend] = useState<Backend | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [me, setMe] = useState<Profile | null>(null);
  const [toasts, setToasts] = useState<{ id: number; text: string }[]>([]);
  const toastId = useRef(0);

  useEffect(() => {
    let off = () => undefined as void;
    let cancelled = false;
    getBackend().then(async (b) => {
      if (cancelled) return;
      if (!b) return setStatus("unavailable");
      await b.auth.whenReady();
      if (cancelled) return;
      setBackend(b);
      setUser(b.auth.current());
      setStatus("ready");
      off = b.auth.onChange(setUser);
    });
    return () => {
      cancelled = true;
      off();
    };
  }, []);

  // Make sure the signed-in user has a profile (name + @handle), then load it.
  useEffect(() => {
    if (!backend || !user) return;
    let cancelled = false;
    const load = () =>
      backend.ensureProfile(user)
        .then(() => backend.getProfiles([user.uid]))
        .then((p) => !cancelled && setMe(p[0] ?? null))
        .catch(() => undefined);
    load();
    const off = backend.subscribe(load);
    return () => {
      cancelled = true;
      off();
    };
  }, [backend, user]);

  const toast = useCallback((text: string) => {
    const id = ++toastId.current;
    setToasts((t) => [...t, { id, text }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4000);
  }, []);

  const run = useCallback(async (fn: () => Promise<unknown>) => {
    try {
      await fn();
      return true;
    } catch (e) {
      toast(e instanceof FeedError ? FEED_MESSAGES[e.code] : e instanceof AuthError ? "Please log in again." : "Something went wrong.");
      return false;
    }
  }, [toast]);

  const requireAuth = useCallback(() => {
    if (user) return true;
    router.push(`/login?next=${encodeURIComponent(pathname || "/home")}`);
    return false;
  }, [user, router, pathname]);

  const myProfile = user && me?.id === user.uid ? me : null;
  const value = useMemo<Ctx>(() => ({ status, backend, user, me: myProfile, toast, run, requireAuth }),
    [status, backend, user, myProfile, toast, run, requireAuth]);

  return (
    <WebContext.Provider value={value}>
      {children}
      <div className="w-toasts" role="status" aria-live="polite">
        {toasts.map((t) => <div key={t.id} className="w-toast">{t.text}</div>)}
      </div>
    </WebContext.Provider>
  );
}

/**
 * Loads data and reloads it after any write, when the tab regains focus, and every 30 s while
 * visible. `deps` should include everything `load` reads.
 */
export function useQuery<T>(load: (b: Backend) => Promise<T>, deps: unknown[], enabled = true) {
  const { backend } = useWeb();
  const key = JSON.stringify(deps);
  const [result, setResult] = useState<{ key: string; data?: T; error?: unknown }>({ key: "" });
  const generation = useRef(0);
  const loadRef = useRef(load);
  useEffect(() => { loadRef.current = load; });

  const refetch = useCallback(() => {
    if (!backend || !enabled) return;
    const g = ++generation.current;
    loadRef.current(backend)
      .then((data) => g === generation.current && setResult({ key, data }))
      .catch((error) => g === generation.current && setResult({ key, error }));
  }, [backend, enabled, key]);

  useEffect(() => {
    refetch();
    if (!backend || !enabled) return;
    const off = backend.subscribe(refetch);
    const onVisible = () => document.visibilityState === "visible" && refetch();
    document.addEventListener("visibilitychange", onVisible);
    // Realtime pushes changes as they happen; this slow poll is only a safety net.
    const timer = backend.kind === "cloud" ? setInterval(onVisible, 60_000) : undefined;
    return () => {
      off();
      document.removeEventListener("visibilitychange", onVisible);
      if (timer) clearInterval(timer);
    };
  }, [backend, enabled, refetch]);

  const current = result.key === key;
  return { data: current ? result.data : undefined, error: current ? result.error ?? null : null, loading: !current, refetch };
}

export function profileMap(list: Profile[]): Map<string, Profile> {
  return new Map(list.map((p) => [p.id, p]));
}
