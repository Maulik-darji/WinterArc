import type { Metadata } from "next";
import { ResetPasswordPage } from "@/components/web/reset-password-page";

export const metadata: Metadata = { title: "Reset password · WinterArc" };

export default function Page() {
  return <ResetPasswordPage />;
}
