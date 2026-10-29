import type { Metadata } from "next";
import { PostScreen } from "@/components/web/post-screen";

export const metadata: Metadata = { title: "Post · WinterArc" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PostScreen id={id} />;
}
