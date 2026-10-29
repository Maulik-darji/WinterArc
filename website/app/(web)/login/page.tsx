import type { Metadata } from "next";
import { AuthPage } from "@/components/web/auth-page";

export const metadata: Metadata = { title: "Log in · WinterArc" };

export default function Page() {
  return <AuthPage initialMode="login" />;
}
