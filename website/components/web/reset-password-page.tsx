"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Eye, EyeOff } from "lucide-react";
import { AUTH_MESSAGES, AuthError } from "@/lib/web/backend";
import { newPasswordProblem } from "@/lib/web/credentials";
import { useWeb } from "./providers";
import { Logo } from "./ui";

function Field({ id, label, value, onChange, error }: {
  id: string; label: string; value: string; onChange: (v: string) => void; error?: string;
}) {
  const [shown, setShown] = useState(false);
  return (
    <div className="w-field">
      <label htmlFor={id}>{label}</label>
      <div className={`w-input-wrap${error ? " invalid" : ""}`}>
        <input id={id} type={shown ? "text" : "password"} value={value} autoComplete="new-password"
          aria-invalid={error ? true : undefined} onChange={(e) => onChange(e.target.value)} />
        <button type="button" className="w-icon-btn" aria-label={shown ? "Hide password" : "Show password"} aria-pressed={shown}
          onClick={() => setShown((s) => !s)}>{shown ? <EyeOff size={18} /> : <Eye size={18} />}</button>
      </div>
      {error && <p className="w-error">{error}</p>}
    </div>
  );
}

/** Where the password-reset email lands: Supabase signs the visitor in, and they choose a new password. */
export function ResetPasswordPage() {
  const { status, backend, user } = useWeb();
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [problems, setProblems] = useState<{ password?: string; confirm?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!backend || busy) return;
    const p: { password?: string; confirm?: string } = {};
    const pw = newPasswordProblem(password);
    if (pw) p.password = pw;
    if (!confirm) p.confirm = "Retype your password.";
    else if (!pw && confirm !== password) p.confirm = "Passwords don't match.";
    setProblems(p);
    if (p.password || p.confirm) return;
    setBusy(true);
    setError(null);
    try {
      await backend.auth.updatePassword(password);
      setDone(true);
      setTimeout(() => router.replace("/home"), 1500);
    } catch (err) {
      setError(AUTH_MESSAGES[err instanceof AuthError ? err.code : "UNKNOWN"]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="w-auth">
      <aside className="w-auth-art" aria-hidden="true"><Logo size={96} /><p>Turn goals into visible progress.</p></aside>
      <main className="w-auth-panel">
        <Link href="/" className="w-brand" aria-label="WinterArc, back to the website"><Logo size={34} /><span>WinterArc</span></Link>
        <form className="w-auth-card" onSubmit={submit} noValidate>
          <h1>Choose a new password</h1>
          {status === "loading" ? <p className="w-muted">Checking your link…</p>
            : !user ? (
              <>
                <p className="w-alert" role="alert">This reset link is invalid or has expired.</p>
                <Link href="/login" className="w-btn w-btn-primary w-btn-block">Back to log in</Link>
              </>
            ) : done ? <p className="w-notice" role="status">Your password was updated. Taking you home…</p>
            : (
              <>
                <Field id="password" label="New password" value={password} error={problems.password}
                  onChange={(v) => { setPassword(v); setProblems((x) => ({ ...x, password: undefined })); }} />
                <p className="w-muted w-small">At least 8 characters, with a letter and a number.</p>
                <Field id="confirm" label="Confirm new password" value={confirm} error={problems.confirm}
                  onChange={(v) => { setConfirm(v); setProblems((x) => ({ ...x, confirm: undefined })); }} />
                {error && <p className="w-alert" role="alert">{error}</p>}
                <button type="submit" className="w-btn w-btn-primary w-btn-block" disabled={busy}>{busy ? "Please wait…" : "Update password"}</button>
              </>
            )}
        </form>
      </main>
    </div>
  );
}
