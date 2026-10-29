"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ArrowLeft } from "lucide-react";
import { handleFromAccount } from "@/lib/web/handles";
import type { Profile } from "@/lib/web/types";
import { EditProfileDialog } from "./edit-profile-dialog";
import { PostCard } from "./post-card";
import { useQuery, useWeb } from "./providers";
import { Avatar } from "./ui";

export function ProfileScreen({ id }: { id: string }) {
  const { user, me, backend, run, requireAuth, toast } = useWeb();
  const [editing, setEditing] = useState(false);
  const router = useRouter();
  const { data, loading, error } = useQuery(async (b) => {
    const [profiles, stats, posts] = await Promise.all([
      b.getProfiles([id]), b.followStats(id, user?.uid ?? null), b.listPosts({ viewerId: user?.uid ?? null, authorId: id }),
    ]);
    return { profile: profiles[0] ?? null, stats, posts };
  }, [id, user?.uid]);

  const mine = user?.uid === id;
  // Your own page always shows, even before the shared profile row exists (it is created on sign-in).
  const fallback: Profile | null = mine && user ? {
    id: user.uid,
    displayName: user.displayName?.trim() || user.email?.split("@")[0] || "WinterArc member",
    handle: handleFromAccount(user.email, user.displayName),
    bio: "",
    avatarUrl: null,
    bannerUrl: null,
  } : null;
  const profile = data?.profile ?? (mine ? me ?? fallback : null);
  return (
    <>
      <div className="w-topbar w-topbar-row">
        <button className="w-icon-btn" aria-label="Back" onClick={() => (window.history.length > 1 ? router.back() : router.push("/home"))}><ArrowLeft size={20} /></button>
        <div><h1>{profile?.displayName ?? "Profile"}</h1>{data && <span className="w-muted w-small">{data.posts.length} {data.posts.length === 1 ? "post" : "posts"}</span>}</div>
      </div>
      {error ? <div className="w-empty"><p className="w-muted">Couldn&apos;t load this profile. Try again.</p></div>
        : loading && !data ? <div className="w-loading" aria-busy="true">Loading…</div>
        : !profile ? <div className="w-empty"><h2>This account doesn&apos;t exist</h2><p className="w-muted">Try searching for another.</p>
            <Link href="/home" className="w-btn w-btn-primary">Back to Home</Link></div>
        : (
          <>
            <div className="w-banner-art" aria-hidden="true"
              style={profile.bannerUrl ? { backgroundImage: `url("${profile.bannerUrl}")` } : undefined} />
            <section className="w-profile">
              <div className="w-profile-top">
                <span className="w-profile-avatar"><Avatar profile={profile} id={id} size={92} /></span>
                {mine && (
                  <button className="w-btn w-btn-outline" onClick={() => setEditing(true)}>Edit profile</button>
                )}
                {!mine && (
                  <button className={`w-btn ${data!.stats.followedByMe ? "w-btn-outline" : "w-btn-dark"}`} aria-pressed={data!.stats.followedByMe}
                    onClick={() => requireAuth() && user && backend && void run(() => backend.toggleFollow(user.uid, id))}>
                    {data!.stats.followedByMe ? "Following" : "Follow"}
                  </button>
                )}
              </div>
              <h2 className="w-profile-name">{profile.displayName}</h2>
              <p className="w-muted">{profile.handle}</p>
              {profile.bio && <p className="w-body">{profile.bio}</p>}
              <p className="w-stats"><span><b>{data!.stats.following}</b> Following</span><span><b>{data!.stats.followers}</b> {data!.stats.followers === 1 ? "Follower" : "Followers"}</span></p>
            </section>
            {editing && backend && user && (
              <EditProfileDialog profile={profile} onClose={() => setEditing(false)}
                onSave={(changes) => run(async () => {
                  if (changes.avatar !== undefined) await backend.setProfileImage(user.uid, "avatar", changes.avatar);
                  if (changes.banner !== undefined) await backend.setProfileImage(user.uid, "banner", changes.banner);
                  if (changes.displayName.trim() !== profile.displayName || changes.bio.trim() !== profile.bio) {
                    await backend.updateProfile(user.uid, { displayName: changes.displayName, bio: changes.bio });
                  }
                }).then((ok) => {
                  if (ok) toast("Profile updated");
                  return ok;
                })} />
            )}
            <div className="w-tabs single"><button className="active" role="tab" aria-selected><span>Posts</span></button></div>
            {data!.posts.length === 0
              ? <div className="w-empty"><p className="w-muted">{mine ? "You haven't posted yet." : "No posts yet."}</p></div>
              : data!.posts.map((p) => <PostCard key={p.id} post={p} author={profile} />)}
          </>
        )}
    </>
  );
}
