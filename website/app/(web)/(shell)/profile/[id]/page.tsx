import type { Metadata } from "next";
import { ProfileScreen } from "@/components/web/profile-screen";

export const metadata: Metadata = { title: "Profile · WinterArc" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ProfileScreen id={decodeURIComponent(id)} />;
}
