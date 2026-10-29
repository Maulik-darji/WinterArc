import type { ReactNode } from "react";
import { Shell } from "@/components/web/shell";

export default function ShellLayout({ children }: { children: ReactNode }) {
  return <Shell>{children}</Shell>;
}
