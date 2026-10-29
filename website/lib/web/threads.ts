import { MAX_VISUAL_DEPTH } from "./rules";
import type { PostComment } from "./types";

/**
 * Turns a flat comment list into display order: pre-order (each comment followed by its replies,
 * oldest first) with the depth and flags needed to draw thread lines. Depth is capped at
 * MAX_VISUAL_DEPTH; deeper replies attach under their ancestor at the cap and carry `replyingTo`.
 * Deleted comments with no visible replies are dropped. Port of the Android CommentThreads.
 */
export type ThreadNode = {
  comment: PostComment;
  depth: number;
  /** For each indent column c (0..depth-1): does the line in that column carry on below this row. */
  continues: boolean[];
  /** Draw a line down from this comment's avatar. */
  hasChildren: boolean;
  /** Author id of the real parent when the reply was flattened by the cap. */
  replyingTo: string | null;
};

type Tree = Map<string, PostComment[]>;

const push = (map: Tree, key: string, value: PostComment) => {
  const list = map.get(key);
  if (list) list.push(value);
  else map.set(key, [value]);
};

const chronological = (a: PostComment, b: PostComment) =>
  a.createdAt < b.createdAt ? -1 : a.createdAt > b.createdAt ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0;

export function buildThread(comments: PostComment[]): ThreadNode[] {
  const byId = new Map(comments.map((c) => [c.id, c]));
  const real: Tree = new Map();
  const roots: PostComment[] = [];
  for (const c of comments) {
    if (c.parentId == null || !byId.has(c.parentId)) roots.push(c);
    else push(real, c.parentId, c);
  }
  roots.sort(chronological);
  for (const list of real.values()) list.sort(chronological);

  const visual: Tree = new Map();
  const replyingTo = new Map<string, string>();
  const attach = (node: PostComment, depth: number, capAncestor: PostComment | null) => {
    for (const kid of real.get(node.id) ?? []) {
      if (depth + 1 <= MAX_VISUAL_DEPTH) {
        push(visual, node.id, kid);
        attach(kid, depth + 1, depth + 1 === MAX_VISUAL_DEPTH ? node : null);
      } else {
        push(visual, (capAncestor as PostComment).id, kid);
        replyingTo.set(kid.id, node.authorId);
        attach(kid, depth + 1, capAncestor);
      }
    }
  };
  for (const root of roots) attach(root, 0, null);

  const prune = (siblings: PostComment[]): PostComment[] => {
    const kept: PostComment[] = [];
    for (const c of siblings) {
      const kids = prune(visual.get(c.id) ?? []);
      visual.set(c.id, kids);
      if (!c.deleted || kids.length > 0) kept.push(c);
    }
    return kept;
  };

  const out: ThreadNode[] = [];
  const emit = (c: PostComment, depth: number, parentContinues: boolean[], hasNextSibling: boolean) => {
    const continues = new Array<boolean>(depth).fill(false);
    for (let i = 0; i < Math.min(parentContinues.length, depth); i++) continues[i] = parentContinues[i];
    if (depth > 0) continues[depth - 1] = hasNextSibling;
    const kids = visual.get(c.id) ?? [];
    out.push({ comment: c, depth, continues, hasChildren: kids.length > 0, replyingTo: replyingTo.get(c.id) ?? null });
    kids.forEach((kid, i) => emit(kid, Math.min(depth + 1, MAX_VISUAL_DEPTH), continues, i < kids.length - 1));
  };
  const visibleRoots = prune(roots);
  visibleRoots.forEach((r, i) => emit(r, 0, [], i < visibleRoots.length - 1));
  return out;
}
