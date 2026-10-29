"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { COMMENT_MAX } from "@/lib/web/rules";
import { buildThread, type ThreadNode } from "@/lib/web/threads";
import { compactCount, exactTime } from "@/lib/web/time";
import { Composer } from "./composer";
import { PostActions, usePostOwnerActions } from "./post-card";
import { profileMap, useQuery, useWeb } from "./providers";
import { CommentRow } from "./thread";
import { Avatar, OwnerMenu, ProfileLink } from "./ui";

export function PostScreen({ id }: { id: string }) {
  const { user, me, backend, run, requireAuth } = useWeb();
  const router = useRouter();
  const [target, setTarget] = useState<ThreadNode | null>(null);
  const box = useRef<HTMLTextAreaElement>(null);

  const { data, loading, error } = useQuery(async (b) => {
    const [post, comments] = await Promise.all([b.getPost(id, user?.uid ?? null), b.listComments(id)]);
    const ids = new Set(comments.map((c) => c.authorId));
    if (post) ids.add(post.authorId);
    return { post, comments, profiles: profileMap(await b.getProfiles([...ids])) };
  }, [id, user?.uid]);

  const thread = useMemo(() => buildThread(data?.comments ?? []), [data?.comments]);
  const post = data?.post ?? null;
  const owner = usePostOwnerActions(post ?? { id, authorId: "", body: "", createdAt: "", editedAt: null, likeCount: 0, likedByMe: false, replyCount: 0 }, () => router.push("/home"));

  // If the comment we are replying to is deleted meanwhile, fall back to replying to the post.
  const activeTarget = target && thread.some((n) => n.comment.id === target.comment.id && !n.comment.deleted) ? target : null;
  useEffect(() => {
    if (data?.post && user && new URLSearchParams(window.location.search).get("reply") === "1") box.current?.focus();
  }, [data?.post, user]);

  const replyTo = (node: ThreadNode | null) => {
    if (!requireAuth()) return;
    setTarget(node);
    box.current?.focus();
    box.current?.scrollIntoView({ block: "center", behavior: "smooth" });
  };

  const author = post ? data?.profiles.get(post.authorId) : undefined;
  const replyingHandle = activeTarget ? data?.profiles.get(activeTarget.comment.authorId)?.handle ?? "@member" : author?.handle ?? null;

  return (
    <>
      <div className="w-topbar w-topbar-row">
        <button className="w-icon-btn" aria-label="Back" onClick={() => (window.history.length > 1 ? router.back() : router.push("/home"))}><ArrowLeft size={20} /></button>
        <h1>Post</h1>
      </div>

      {error ? <div className="w-empty"><p className="w-muted">Couldn&apos;t load this post. Try again.</p></div>
        : loading && !data ? <div className="w-loading" aria-busy="true">Loading…</div>
        : !post ? (
          <div className="w-empty"><h2>This post was deleted</h2><p className="w-muted">It may have been removed by its author.</p>
            <Link href="/home" className="w-btn w-btn-primary">Back to Home</Link></div>
        ) : (
          <>
            <article className="w-post-full">
              <header className="w-post-full-head">
                <ProfileLink id={post.authorId} label={`View profile of ${author?.displayName ?? "member"}`}><Avatar profile={author} id={post.authorId} size={46} /></ProfileLink>
                <ProfileLink id={post.authorId} className="w-post-full-name">
                  <b>{author?.displayName ?? "WinterArc member"}</b><span className="w-muted">{author?.handle ?? "@member"}</span>
                </ProfileLink>
                {user?.uid === post.authorId && <OwnerMenu label="Post options" onEdit={() => owner.open("edit")} onDelete={() => owner.open("delete")} />}
              </header>
              <p className="w-body w-body-large">{post.body}</p>
              <p className="w-muted w-stamp">{exactTime(post.createdAt)}{post.editedAt ? " · Edited" : ""}</p>
              <div className="w-stats">
                <span><b>{compactCount(post.replyCount) || 0}</b> {post.replyCount === 1 ? "Reply" : "Replies"}</span>
                <span><b>{compactCount(post.likeCount) || 0}</b> {post.likeCount === 1 ? "Like" : "Likes"}</span>
              </div>
              <PostActions post={post} author={author} large onReply={() => replyTo(null)} />
              {owner.node}
            </article>

            {user && backend ? (
              <Composer me={me ?? undefined} userId={user.uid} placeholder="Post your reply" submitLabel="Reply" max={COMMENT_MAX} inputRef={box}
                replyingTo={replyingHandle} onCancelReply={activeTarget ? () => setTarget(null) : undefined}
                onSubmit={(text) => run(() => backend.addComment(user.uid, post.id, activeTarget?.comment.id ?? null, text)).then((ok) => { if (ok) setTarget(null); return ok; })} />
            ) : (
              <div className="w-join w-join-inline"><span className="w-muted">Log in to reply.</span>
                <Link href={`/login?next=/post/${post.id}`} className="w-btn w-btn-primary w-btn-sm">Log in</Link></div>
            )}

            <section aria-label="Replies">
              {thread.length === 0 ? <div className="w-empty"><p className="w-muted">No replies yet. Start the conversation.</p></div>
                : thread.map((n) => <CommentRow key={n.comment.id} node={n} profiles={data!.profiles} onReply={replyTo} />)}
            </section>
          </>
        )}
    </>
  );
}
