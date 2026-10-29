"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { POST_MAX } from "@/lib/web/rules";
import { Composer } from "./composer";
import { PostCard } from "./post-card";
import { profileMap, useQuery, useWeb } from "./providers";

export function HomeScreen() {
  const { user, me, backend, run } = useWeb();
  const router = useRouter();
  const [tab, setTab] = useState<"all" | "following">("all");
  const box = useRef<HTMLTextAreaElement>(null);

  const { data, loading, error } = useQuery(async (b) => {
    const posts = await b.listPosts({ viewerId: user?.uid ?? null, following: tab === "following" });
    const profiles = await b.getProfiles([...new Set(posts.map((p) => p.authorId))]);
    return { posts, profiles: profileMap(profiles) };
  }, [user?.uid, tab]);

  useEffect(() => {
    if (user && window.location.hash === "#compose") box.current?.focus();
  }, [user, data]);

  const following = tab === "following";
  return (
    <>
      <div className="w-topbar">
        <h1 className="w-sr">Home</h1>
        <div className="w-tabs" role="tablist" aria-label="Timeline">
          {([["all", "For you"], ["following", "Following"]] as const).map(([key, label]) => (
            <button key={key} role="tab" aria-selected={tab === key} className={tab === key ? "active" : ""}
              onClick={() => { if (key === "following" && !user) return router.push("/login?next=/home"); setTab(key); }}>
              <span>{label}</span>
            </button>
          ))}
        </div>
      </div>

      {user && backend && !following && (
        <Composer me={me ?? undefined} userId={user.uid} placeholder="What's happening with your goals?" submitLabel="Post" max={POST_MAX}
          inputRef={box} onSubmit={(text) => run(() => backend.createPost(user.uid, text))} />
      )}

      {error ? <div className="w-empty"><p className="w-muted">Couldn&apos;t load posts. Check your connection and try again.</p></div>
        : loading && !data ? <div className="w-loading" aria-busy="true">Loading…</div>
        : data && data.posts.length === 0 ? (
          <div className="w-empty">
            <h2>{following ? "Nobody to show yet" : "No posts yet"}</h2>
            <p className="w-muted">{following ? "Posts from people you follow show up here." : "Share a win, a setback or what you're working on. Your post starts the conversation."}</p>
          </div>
        ) : data?.posts.map((p) => <PostCard key={p.id} post={p} author={data.profiles.get(p.authorId)} />)}

      {!user && (
        <div className="w-join">
          <div>
            <b>Join the conversation</b>
            <span className="w-muted">Log in to post, reply and follow people.</span>
          </div>
          <Link href="/login?next=/home" className="w-btn w-btn-outline">Log in</Link>
          <Link href="/signup?next=/home" className="w-btn w-btn-primary">Sign up</Link>
        </div>
      )}
    </>
  );
}
