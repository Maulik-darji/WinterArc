"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Heart, MessageCircle, Share2 } from "lucide-react";
import type { Post, Profile } from "@/lib/web/types";
import { POST_MAX } from "@/lib/web/rules";
import { compactCount, exactTime, relativeTime } from "@/lib/web/time";
import { EditDialog } from "./composer";
import { useWeb } from "./providers";
import { Avatar, ConfirmDialog, OwnerMenu, ProfileLink } from "./ui";

export function useSharePost() {
  const { toast } = useWeb();
  return async (post: Post, author?: Profile) => {
    const url = `${window.location.origin}/post/${post.id}`;
    try {
      if (navigator.share) await navigator.share({ title: `${author?.displayName ?? "A member"} on WinterArc`, text: post.body, url });
      else {
        await navigator.clipboard.writeText(url);
        toast("Link copied");
      }
    } catch {
      /* the user dismissed the share sheet */
    }
  };
}

export function PostActions({ post, author, large = false, onReply }: {
  post: Post; author?: Profile; large?: boolean; onReply?: () => void;
}) {
  const { backend, user, run, requireAuth } = useWeb();
  const router = useRouter();
  const share = useSharePost();
  const size = large ? 22 : 18;
  return (
    <div className={`w-actions${large ? " large" : ""}`} onClick={(e) => e.stopPropagation()}>
      <button className="w-action reply" aria-label={`Reply. ${post.replyCount} replies`}
        onClick={() => (onReply ? onReply() : router.push(`/post/${post.id}?reply=1`))}>
        <MessageCircle size={size} /><span>{large ? "" : compactCount(post.replyCount)}</span>
      </button>
      <button className={`w-action like${post.likedByMe ? " on" : ""}`} aria-pressed={post.likedByMe}
        aria-label={`${post.likedByMe ? "Unlike" : "Like"}. ${post.likeCount} likes`}
        onClick={() => requireAuth() && user && backend && void run(() => backend.toggleLike(user.uid, post.id))}>
        <Heart size={size} fill={post.likedByMe ? "currentColor" : "none"} /><span>{large ? "" : compactCount(post.likeCount)}</span>
      </button>
      <button className="w-action share" aria-label="Share post" onClick={() => void share(post, author)}>
        <Share2 size={size} />
      </button>
    </div>
  );
}

/** Delete + edit dialogs for one of the viewer's own posts. */
export function usePostOwnerActions(post: Post, afterDelete?: () => void) {
  const { backend, user, run } = useWeb();
  const [dialog, setDialog] = useState<"edit" | "delete" | null>(null);
  const node = dialog && backend && user ? (
    dialog === "edit" ? (
      <EditDialog title="Edit post" initial={post.body} max={POST_MAX} onClose={() => setDialog(null)}
        onSave={(text) => run(() => backend.editPost(user.uid, post.id, text))} />
    ) : (
      <ConfirmDialog title="Delete post?" action="Delete" onClose={() => setDialog(null)}
        body="This removes the post and all of its replies. This can't be undone."
        onConfirm={() => void run(() => backend.deletePost(user.uid, post.id)).then((ok) => ok && afterDelete?.())} />
    )
  ) : null;
  return { open: setDialog, node };
}

export function PostCard({ post, author }: { post: Post; author?: Profile }) {
  const { user } = useWeb();
  const router = useRouter();
  const owner = usePostOwnerActions(post);
  const mine = user?.uid === post.authorId;
  return (
    <article className="w-post" onClick={() => router.push(`/post/${post.id}`)}>
      <ProfileLink id={post.authorId} label={`View profile of ${author?.displayName ?? "member"}`}>
        <Avatar profile={author} id={post.authorId} />
      </ProfileLink>
      <div className="w-post-main">
        <header className="w-post-head">
          <ProfileLink id={post.authorId} className="w-name">{author?.displayName ?? "WinterArc member"}</ProfileLink>
          <span className="w-muted w-handle">{author?.handle ?? "@member"}</span>
          <span className="w-muted" aria-hidden="true">·</span>
          <Link href={`/post/${post.id}`} className="w-muted w-time" title={exactTime(post.createdAt)} onClick={(e) => e.stopPropagation()}>
            {relativeTime(post.createdAt)}
          </Link>
          {post.editedAt && <span className="w-muted w-edited">· Edited</span>}
          {mine && <OwnerMenu label="Post options" onEdit={() => owner.open("edit")} onDelete={() => owner.open("delete")} />}
        </header>
        <p className="w-body">{post.body}</p>
        <PostActions post={post} author={author} />
      </div>
      {owner.node}
    </article>
  );
}
