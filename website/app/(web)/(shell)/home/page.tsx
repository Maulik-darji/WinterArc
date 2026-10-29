import type { Metadata } from "next";
import { HomeScreen } from "@/components/web/home-screen";

export const metadata: Metadata = { title: "Home · WinterArc" };

export default function Page() {
  return <HomeScreen />;
}
