"use client";

import { useState } from "react";
import { MessageCircle } from "lucide-react";
import { COMMENT_MAX } from "@/lib/web/rules";
import type { ThreadNode } from "@/lib/web/threads";
import type { Profile } from "@/lib/web/types";
import { relativeTime, exactTime } from "@/lib/web/time";
import { EditDialog } from "./composer";
import { useWeb } from "./providers";
import { Avatar, ConfirmDialog, OwnerMenu, ProfileLink } from "./ui";

/**
 * One comment in a thread. Each indent level is a column; CSS draws the connector lines from the
 * flags that buildThread computed (pass-through, elbow into this avatar, line down to replies).
 */
export function CommentRow({ node, profiles, onReply }: {
  node: ThreadNode; profiles: Map<string, Profile>; onReply: (node: ThreadNode) => void;
}) {
  const { backend, user, run } = useWeb();
  const [dialog, setDialog] = useState<"edit" | "delete" | null>(null);
  const c = node.comment;
  const author = profiles.get(c.authorId);
  const mine = !c.deleted && user?.uid === c.authorId;
  const replyingTo = node.replyingTo ? profiles.get(node.replyingTo)?.handle ?? "@member" : null;

  return (
    <article className={`w-comment${node.depth === 0 ? " w-comment-root" : ""}`} aria-label={c.deleted ? "Deleted comment" : `Reply by ${author?.displayName ?? "member"}, level ${node.depth + 1}`}>
      {node.continues.map((carries, col) => {
        const last = col === node.depth - 1;
        return <span key={col} className={`tl-col${last ? " elbow" : carries ? " pass" : ""}${last && carries ? " more" : ""}`} aria-hidden="true" />;
      })}
      <div className="tl-av">
        {c.deleted ? <Avatar id={c.id} size={36} muted /> : (
          <ProfileLink id={c.authorId} label={`View profile of ${author?.displayName ?? "member"}`}>
            <Avatar profile={author} id={c.authorId} size={36} />
          </ProfileLink>
        )}
        {node.hasChildren && <i className="tl-down" aria-hidden="true" />}
      </div>
      <div className="w-comment-main">
        {c.deleted ? <p className="w-body w-deleted">This comment was deleted.</p> : (
          <>
            <header className="w-post-head">
              <ProfileLink id={c.authorId} className="w-name">{author?.displayName ?? "WinterArc member"}</ProfileLink>
              <span className="w-muted w-handle">{author?.handle ?? "@member"}</span>
              <span className="w-muted" aria-hidden="true">·</span>
              <time className="w-muted w-time" dateTime={c.createdAt} title={exactTime(c.createdAt)}>{relativeTime(c.createdAt)}</time>
              {c.editedAt && <span className="w-muted w-edited">· Edited</span>}
              {mine && <OwnerMenu label="Comment options" onEdit={() => setDialog("edit")} onDelete={() => setDialog("delete")} />}
            </header>
            {replyingTo && <p className="w-muted w-replying-to">Replying to <span>{replyingTo}</span></p>}
            <p className="w-body">{c.body}</p>
            <div className="w-actions">
              <button className="w-action reply" aria-label={`Reply to ${author?.displayName ?? "member"}`} onClick={() => onReply(node)}><MessageCircle size={16} /><span>Reply</span></button>
            </div>
          </>
        )}
      </div>
      {dialog === "edit" && backend && user && (
        <EditDialog title="Edit comment" initial={c.body} max={COMMENT_MAX} onClose={() => setDialog(null)}
          onSave={(text) => run(() => backend.editComment(user.uid, c.id, text))} />
      )}
      {dialog === "delete" && backend && user && (
        <ConfirmDialog title="Delete comment?" action="Delete" onClose={() => setDialog(null)}
          body="Replies to it will stay visible. This can't be undone."
          onConfirm={() => void run(() => backend.deleteComment(user.uid, c.id))} />
      )}
    </article>
  );
}
