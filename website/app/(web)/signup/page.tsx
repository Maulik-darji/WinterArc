import type { Metadata } from "next";
import { AuthPage } from "@/components/web/auth-page";

export const metadata: Metadata = { title: "Sign up · WinterArc" };

export default function Page() {
  return <AuthPage initialMode="signup" />;
}
