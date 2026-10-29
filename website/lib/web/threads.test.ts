import { describe, expect, it } from "vitest";
import { buildThread } from "./threads";
import type { PostComment } from "./types";

let n = 0;
const c = (id: string, parentId: string | null, over: Partial<PostComment> = {}): PostComment => ({
  id, postId: "p", parentId, authorId: "u" + id, body: "text " + id,
  createdAt: new Date(2026, 9, 24, 12, 0, n++).toISOString(), editedAt: null, deleted: false, ...over,
});

describe("buildThread", () => {
  it("orders pre-order, oldest first, with depth", () => {
    const list = buildThread([c("a", null), c("b", "a"), c("d", null), c("e", "a")]);
    expect(list.map((x) => [x.comment.id, x.depth])).toEqual([["a", 0], ["b", 1], ["e", 1], ["d", 0]]);
  });

  it("flags lines: parent has children, siblings continue", () => {
    const list = buildThread([c("a", null), c("b", "a"), c("e", "a"), c("d", null)]);
    const by = Object.fromEntries(list.map((x) => [x.comment.id, x]));
    expect(by.a.hasChildren).toBe(true);
    expect(by.b.continues).toEqual([true]);
    expect(by.e.continues).toEqual([false]);
    expect(by.d.hasChildren).toBe(false);
  });

  it("caps depth at 3 and records who the flattened reply answers", () => {
    const list = buildThread([c("a", null), c("b", "a"), c("c", "b"), c("d", "c"), c("e", "d")]);
    expect(list.map((x) => x.depth)).toEqual([0, 1, 2, 3, 3]);
    expect(list[3].replyingTo).toBeNull();
    expect(list[4].replyingTo).toBe("ud");
  });

  it("keeps a deleted comment that still has replies, drops a deleted leaf", () => {
    const list = buildThread([c("a", null, { deleted: true, body: "" }), c("b", "a"), c("x", null, { deleted: true, body: "" })]);
    expect(list.map((x) => x.comment.id)).toEqual(["a", "b"]);
  });

  it("treats a reply whose parent is missing as top level", () => {
    const list = buildThread([c("b", "gone")]);
    expect(list[0].depth).toBe(0);
  });
});
