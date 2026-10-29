import { describe, expect, it } from "vitest";
import { COMMENT_MAX, POST_MAX, clean, remaining } from "./rules";
import { validateLogIn, validateSignUp } from "./credentials";
import { handleFromAccount } from "./handles";
import { compactCount, relativeTime } from "./time";

describe("clean / remaining", () => {
  it("trims, rejects empty and over-limit, counts code points", () => {
    expect(clean("  hi  ", POST_MAX)).toBe("hi");
    expect(clean("   ", POST_MAX)).toBeNull();
    expect(clean("a".repeat(281), POST_MAX)).toBeNull();
    expect(clean("a".repeat(280), POST_MAX)).not.toBeNull();
    expect(clean("😀".repeat(500), COMMENT_MAX)).not.toBeNull();
    expect(remaining("😀😀", 280)).toBe(278);
    expect(clean("a\n\n\n\nb", POST_MAX)).toBe("a\n\nb");
  });
});

describe("credentials", () => {
  const ok = { name: "Ana", email: "ana@example.com", password: "winter2026", confirm: "winter2026" };
  it("accepts a valid sign-up", () => expect(validateSignUp(ok)).toEqual({}));
  it("flags weak passwords and mismatches", () => {
    expect(validateSignUp({ ...ok, password: "short1", confirm: "short1" }).password).toBeTruthy();
    expect(validateSignUp({ ...ok, password: "allletters", confirm: "allletters" }).password).toMatch(/number/);
    expect(validateSignUp({ ...ok, password: "12345678", confirm: "12345678" }).password).toMatch(/letter/);
    expect(validateSignUp({ ...ok, confirm: "winter2027" }).confirm).toMatch(/match/);
  });
  it("flags name and email", () => {
    expect(validateSignUp({ ...ok, name: " " }).name).toBeTruthy();
    expect(validateSignUp({ ...ok, email: "nope" }).email).toBeTruthy();
  });
  it("login needs email and password", () => {
    expect(validateLogIn({ email: "", password: "" })).toHaveProperty("email");
    expect(validateLogIn({ email: "a@b.co", password: "x" })).toEqual({});
  });
});

describe("handles and time", () => {
  it("builds handles like the app", () => {
    expect(handleFromAccount("Maulik.Darji+x@gmail.com", "M")).toBe("@maulikdarjix");
    expect(handleFromAccount(null, "Ana María")).toBe("@anamara");
    expect(handleFromAccount("a_very_long_local_part_name@x.com", null)).toBe("@a_very_long_loc");
    expect(handleFromAccount(null, null)).toBe("@member");
  });
  it("formats relative time and counts", () => {
    const now = new Date("2026-10-24T12:00:00Z");
    expect(relativeTime("2026-10-24T11:59:40Z", now)).toBe("now");
    expect(relativeTime("2026-10-24T11:55:00Z", now)).toBe("5m");
    expect(relativeTime("2026-10-24T09:00:00Z", now)).toBe("3h");
    expect(relativeTime("2026-10-21T12:00:00Z", now)).toBe("3d");
    expect(compactCount(0)).toBe("");
    expect(compactCount(1200)).toBe("1.2K");
  });
});
