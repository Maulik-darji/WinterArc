"use client";

import { createContext, useContext, useSyncExternalStore, type ReactNode } from "react";

type Mode = "light" | "dark";
const ThemeContext = createContext<{ resolved: Mode; toggle: () => void }>({ resolved: "light", toggle: () => undefined });
export const useTheme = () => useContext(ThemeContext);

const KEY = "wa-theme";
const EVENT = "wa-theme-change";
const DARK = "(prefers-color-scheme: dark)";

function subscribeChoice(cb: () => void) {
  window.addEventListener("storage", cb);
  window.addEventListener(EVENT, cb);
  return () => {
    window.removeEventListener("storage", cb);
    window.removeEventListener(EVENT, cb);
  };
}
function readChoice(): Mode | null {
  try {
    const v = localStorage.getItem(KEY);
    return v === "light" || v === "dark" ? v : null;
  } catch {
    return null;
  }
}
function subscribeSystem(cb: () => void) {
  const query = window.matchMedia(DARK);
  query.addEventListener("change", cb);
  return () => query.removeEventListener("change", cb);
}

/** The `.wa-web` root: follows the system theme until the visitor picks one, then remembers it. */
export function ThemeRoot({ children }: { children: ReactNode }) {
  const choice = useSyncExternalStore(subscribeChoice, readChoice, () => null);
  const system = useSyncExternalStore<Mode>(subscribeSystem, () => (window.matchMedia(DARK).matches ? "dark" : "light"), () => "light");
  const resolved = choice ?? system;
  const toggle = () => {
    try { localStorage.setItem(KEY, resolved === "dark" ? "light" : "dark"); } catch { /* storage unavailable */ }
    window.dispatchEvent(new Event(EVENT));
  };
  return (
    <ThemeContext.Provider value={{ resolved, toggle }}>
      <div className="wa-web" data-theme={choice ?? undefined}>{children}</div>
    </ThemeContext.Provider>
  );
}
