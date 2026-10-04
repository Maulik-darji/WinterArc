import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "WinterArc — Turn goals into visible progress",
  description: "Track consistent habits or progress toward a milestone, check in daily, and see your effort become a record you can build on.",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
