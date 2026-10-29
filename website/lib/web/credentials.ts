/** Email/password rules, matching the Android app (domain/auth/Credentials). */
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;
export const NAME_MAX = 50;

export type Field = "name" | "email" | "password" | "confirm";
export type Problems = Partial<Record<Field, string>>;

export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export function emailProblem(email: string): string | null {
  const e = normalizeEmail(email);
  if (!e) return "Enter your email address.";
  if (!EMAIL.test(e)) return "Enter a valid email address.";
  return null;
}

export function newPasswordProblem(password: string): string | null {
  if (!password) return "Choose a password.";
  if (password.length < PASSWORD_MIN) return `Use at least ${PASSWORD_MIN} characters.`;
  if (password.length > PASSWORD_MAX) return `Use at most ${PASSWORD_MAX} characters.`;
  if (!/\p{L}/u.test(password)) return "Include at least one letter.";
  if (!/\p{N}/u.test(password)) return "Include at least one number.";
  return null;
}

export function validateSignUp(v: { name: string; email: string; password: string; confirm: string }): Problems {
  const p: Problems = {};
  const name = v.name.trim();
  if (!name) p.name = "Enter your name.";
  else if (Array.from(name).length > NAME_MAX) p.name = `Use at most ${NAME_MAX} characters.`;
  const e = emailProblem(v.email);
  if (e) p.email = e;
  const pw = newPasswordProblem(v.password);
  if (pw) p.password = pw;
  if (!v.confirm) p.confirm = "Retype your password.";
  else if (!pw && v.confirm !== v.password) p.confirm = "Passwords don't match.";
  return p;
}

export function validateLogIn(v: { email: string; password: string }): Problems {
  const p: Problems = {};
  const e = emailProblem(v.email);
  if (e) p.email = e;
  if (!v.password) p.password = "Enter your password.";
  return p;
}
