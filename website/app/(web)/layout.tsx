import type { Metadata } from "next";
import type { ReactNode } from "react";
import { WebProvider } from "@/components/web/providers";
import { ThemeRoot } from "@/components/web/theme";
import "./web.css";

export const metadata: Metadata = {
  title: "WinterArc",
  description: "Share your progress, follow people and talk it through in threads.",
};

export default function WebLayout({ children }: { children: ReactNode }) {
  return (
    <ThemeRoot>
      <WebProvider>{children}</WebProvider>
    </ThemeRoot>
  );
}
