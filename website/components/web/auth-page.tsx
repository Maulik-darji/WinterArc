"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { Eye, EyeOff } from "lucide-react";
import { AUTH_MESSAGES, AuthError } from "@/lib/web/backend";
import { validateLogIn, validateSignUp, emailProblem, type Field, type Problems } from "@/lib/web/credentials";
import { useWeb } from "./providers";
import { Logo } from "./ui";

type Mode = "login" | "signup" | "reset";

const TITLES: Record<Mode, string> = {
  login: "Welcome back",
  signup: "Create your account",
  reset: "Reset your password",
};

function safeNext(): string {
  const next = new URLSearchParams(window.location.search).get("next");
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/home";
}

function hasNext(): boolean {
  return new URLSearchParams(window.location.search).has("next");
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.1C12.4 13.6 17.7 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.6 5.9c4.4-4.1 7-10.1 7-17.6z" />
      <path fill="#FBBC05" d="M10.5 28.7a14.5 14.5 0 0 1 0-9.4l-7.9-6.1a24 24 0 0 0 0 21.6l7.9-6.1z" />
      <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.6-5.9c-2.1 1.4-4.9 2.3-8.3 2.3-6.3 0-11.6-4.1-13.5-9.8l-7.9 6.1C6.5 42.6 14.6 48 24 48z" />
    </svg>
  );
}

function PasswordField({ id, label, value, onChange, error, autoComplete }: {
  id: string; label: string; value: string; onChange: (v: string) => void; error?: string; autoComplete: string;
}) {
  const [shown, setShown] = useState(false);
  return (
    <div className="w-field">
      <label htmlFor={id}>{label}</label>
      <div className={`w-input-wrap${error ? " invalid" : ""}`}>
        <input id={id} type={shown ? "text" : "password"} value={value} autoComplete={autoComplete}
          aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
          onChange={(e) => onChange(e.target.value)} />
        <button type="button" className="w-icon-btn" aria-label={shown ? "Hide password" : "Show password"} aria-pressed={shown}
          onClick={() => setShown((s) => !s)}>
          {shown ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
      {error && <p className="w-error" id={`${id}-error`}>{error}</p>}
    </div>
  );
}

function TextField({ id, label, type = "text", value, onChange, error, autoComplete }: {
  id: string; label: string; type?: string; value: string; onChange: (v: string) => void; error?: string; autoComplete: string;
}) {
  return (
    <div className="w-field">
      <label htmlFor={id}>{label}</label>
      <div className={`w-input-wrap${error ? " invalid" : ""}`}>
        <input id={id} type={type} value={value} autoComplete={autoComplete}
          aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
          onChange={(e) => onChange(e.target.value)} />
      </div>
      {error && <p className="w-error" id={`${id}-error`}>{error}</p>}
    </div>
  );
}

export function AuthPage({ initialMode }: { initialMode: "login" | "signup" }) {
  const { status, backend, user } = useWeb();
  const router = useRouter();
  const [mode, setMode] = useState<Mode>(initialMode);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [problems, setProblems] = useState<Problems>({});
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (status === "ready" && user) router.replace(safeNext()); }, [status, user, router]);

  const switchMode = (m: Mode) => {
    setMode(m);
    setProblems({});
    setError(null);
    setNotice(null);
    window.history.replaceState(null, "", (m === "signup" ? "/signup" : "/login") + window.location.search);
  };
  const clear = (field: Field) => setProblems((p) => ({ ...p, [field]: undefined }));

  async function attempt(fn: () => Promise<unknown>, after?: () => void) {
    setBusy(true);
    setError(null);
    try {
      await fn();
      after?.();
    } catch (e) {
      setError(AUTH_MESSAGES[e instanceof AuthError ? e.code : "UNKNOWN"]);
    } finally {
      setBusy(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!backend || busy) return;
    if (mode === "reset") {
      const p = emailProblem(email);
      if (p) return setProblems({ email: p });
      return void attempt(() => backend.auth.sendPasswordReset(email), () => setNotice("If an account exists for that email, a reset link is on its way."));
    }
    const found = mode === "signup" ? validateSignUp({ name, email, password, confirm }) : validateLogIn({ email, password });
    setProblems(found);
    if (Object.keys(found).length > 0) return;
    if (mode === "signup") {
      void attempt(async () => {
        const created = await backend.auth.signUp(name, email, password);
        // A brand-new account lands on its own profile, unless it came here to do something else.
        if (created) return router.replace(hasNext() ? safeNext() : `/profile/${encodeURIComponent(created.uid)}`);
        // Supabase sent a confirmation link; the account is usable once it has been opened.
        setPassword("");
        setConfirm("");
        setMode("login");
        window.history.replaceState(null, "", "/login" + window.location.search);
        setNotice(`We sent a confirmation link to ${email.trim()}. Open it to activate your account, then log in.`);
      });
      return;
    }
    void attempt(() => backend.auth.signIn(email, password), () => router.replace(safeNext()));
  }

  const unavailable = status === "unavailable";

  return (
    <div className="w-auth">
      <aside className="w-auth-art" aria-hidden="true">
        <Logo size={96} />
        <p>Turn goals into visible progress.</p>
      </aside>
      <main className="w-auth-panel">
        <Link href="/" className="w-brand" aria-label="WinterArc, back to the website"><Logo size={34} /><span>WinterArc</span></Link>
        <form className="w-auth-card" onSubmit={submit} noValidate>
          <h1>{TITLES[mode]}</h1>
          {mode === "signup" && <p className="w-muted">Use your email and a password you don&apos;t use anywhere else.</p>}
          {mode === "reset" && <p className="w-muted">Enter your email and we&apos;ll send you a link to choose a new password.</p>}

          {unavailable && <p className="w-alert" role="alert">{AUTH_MESSAGES.NOT_CONFIGURED}</p>}
          {backend?.auth.testHint && <p className="w-hint">{backend.auth.testHint}</p>}

          {mode !== "reset" && (
            <>
              <button type="button" className="w-btn w-btn-outline w-btn-block" disabled={busy || unavailable}
                onClick={() => backend && void attempt(async () => {
                  const signedIn = await backend.auth.signInWithGoogle(safeNext());
                  if (signedIn) router.replace(safeNext()); // null: the browser is heading to Google
                })}>
                <GoogleIcon /> Continue with Google
              </button>
              <div className="w-or"><span>or</span></div>
            </>
          )}

          {mode === "signup" && <TextField id="name" label="Your name" value={name} autoComplete="name" error={problems.name}
            onChange={(v) => { setName(v); clear("name"); }} />}
          <TextField id="email" label="Email" type="email" value={email} autoComplete="email" error={problems.email}
            onChange={(v) => { setEmail(v); clear("email"); }} />
          {mode !== "reset" && (
            <PasswordField id="password" label="Password" value={password} error={problems.password}
              autoComplete={mode === "signup" ? "new-password" : "current-password"}
              onChange={(v) => { setPassword(v); clear("password"); }} />
          )}
          {mode === "signup" && (
            <>
              <p className="w-muted w-small">At least 8 characters, with a letter and a number.</p>
              <PasswordField id="confirm" label="Confirm password" value={confirm} error={problems.confirm} autoComplete="new-password"
                onChange={(v) => { setConfirm(v); clear("confirm"); }} />
            </>
          )}

          {error && <p className="w-alert" role="alert">{error}</p>}
          {notice && <p className="w-notice" role="status">{notice}</p>}

          <button type="submit" className="w-btn w-btn-primary w-btn-block" disabled={busy || unavailable}>
            {busy ? "Please wait…" : mode === "signup" ? "Create account" : mode === "login" ? "Log in" : "Send reset link"}
          </button>

          {mode === "login" && <button type="button" className="w-link" onClick={() => switchMode("reset")}>Forgot password?</button>}
          <p className="w-switch w-muted">
            {mode === "signup" ? "Already have an account? " : mode === "login" ? "New to WinterArc? " : "Remembered it? "}
            <button type="button" className="w-link" onClick={() => switchMode(mode === "signup" ? "login" : mode === "login" ? "signup" : "login")}>
              {mode === "signup" ? "Log in" : mode === "login" ? "Create an account" : "Back to log in"}
            </button>
          </p>
          <p className="w-muted w-small"><Link href="/home" className="w-link">Keep browsing without an account</Link></p>
        </form>
      </main>
    </div>
  );
}
