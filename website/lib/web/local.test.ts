import { beforeEach, describe, expect, it } from "vitest";
import { AuthError, FeedError, type Backend } from "./backend";
import { createLocalBackend } from "./local";

function memory() {
  const m = new Map<string, string>();
  return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v) };
}

let b: Backend;
let n = 0;
beforeEach(() => {
  n = 0;
  b = createLocalBackend(memory(), { now: () => new Date(Date.UTC(2026, 9, 24, 12, 0, n++)), id: () => "id" + n++ });
});

const code = async (p: Promise<unknown>) => p.then(() => null, (e: FeedError | AuthError) => e.code);

describe("local auth", () => {
  it("signs up, rejects duplicates, signs in and out", async () => {
    const u = (await b.auth.signUp("Ana", "Ana@Example.com", "winter2026"))!;
    expect(b.auth.current()?.uid).toBe(u.uid);
    expect(await code(b.auth.signUp("Ana", "ana@example.com", "winter2026"))).toBe("EMAIL_IN_USE");
    await b.auth.signOut();
    expect(b.auth.current()).toBeNull();
    expect(await code(b.auth.signIn("ana@example.com", "wrong1234"))).toBe("WRONG_CREDENTIALS");
    expect((await b.auth.signIn("ana@example.com", "winter2026")).uid).toBe(u.uid);
    await b.auth.updatePassword("spring2027");
    await b.auth.signOut();
    expect(await code(b.auth.signIn("ana@example.com", "winter2026"))).toBe("WRONG_CREDENTIALS");
    expect((await b.auth.signIn("ana@example.com", "spring2027")).uid).toBe(u.uid);
  });
});

describe("local profiles", () => {
  it("creates a profile once and lets the owner edit name and bio", async () => {
    const user = { uid: "me", email: "maya.r@example.com", displayName: "Maya Rao", emailVerified: true };
    await b.ensureProfile(user);
    await b.ensureProfile({ ...user, displayName: "Changed" });
    expect(await b.getProfiles(["me"])).toEqual([
      { id: "me", displayName: "Maya Rao", handle: "@mayar", bio: "", avatarUrl: null, bannerUrl: null },
    ]);
    await b.setProfileImage("me", "avatar", new Blob([new Uint8Array([1, 2, 3])], { type: "image/jpeg" }));
    expect((await b.getProfiles(["me"]))[0].avatarUrl).toBe("data:image/jpeg;base64,AQID");
    await b.setProfileImage("me", "avatar", null);
    expect((await b.getProfiles(["me"]))[0].avatarUrl).toBeNull();
    expect(await code(b.setProfileImage("nobody", "banner", null))).toBe("not_found");
    await b.updateProfile("me", { displayName: "  Maya  ", bio: "Runner. Reader." });
    expect((await b.getProfiles(["me"]))[0]).toMatchObject({ displayName: "Maya", bio: "Runner. Reader." });
    expect(await code(b.updateProfile("me", { displayName: "M", bio: "" }))).toBe("invalid");
    expect(await code(b.updateProfile("me", { displayName: "Maya", bio: "x".repeat(161) }))).toBe("invalid");
  });
});

describe("local feed", () => {
  it("creates, likes, edits and deletes posts with authorship checks", async () => {
    await b.createPost("me", "  hello  ");
    const [post] = await b.listPosts({ viewerId: "me" });
    expect(post.body).toBe("hello");
    await b.toggleLike("other", post.id);
    expect((await b.getPost(post.id, "other"))!.likedByMe).toBe(true);
    expect((await b.getPost(post.id, "me"))!.likeCount).toBe(1);
    expect(await code(b.editPost("other", post.id, "x"))).toBe("denied");
    await b.editPost("me", post.id, "hello again");
    expect((await b.getPost(post.id, "me"))!.editedAt).not.toBeNull();
    expect(await code(b.deletePost("other", post.id))).toBe("denied");
    await b.deletePost("me", post.id);
    expect(await b.getPost(post.id, "me")).toBeNull();
  });

  it("rejects empty and over-limit posts", async () => {
    expect(await code(b.createPost("me", "   "))).toBe("invalid");
    expect(await code(b.createPost("me", "a".repeat(281)))).toBe("invalid");
  });

  it("threads replies; deleting a parent with replies leaves a placeholder, then cleans up", async () => {
    await b.createPost("me", "post");
    const [post] = await b.listPosts({ viewerId: "me" });
    await b.addComment("a", post.id, null, "top");
    const top = (await b.listComments(post.id))[0];
    await b.addComment("b", post.id, top.id, "reply");
    const reply = (await b.listComments(post.id)).find((c) => c.parentId === top.id)!;
    expect((await b.getPost(post.id, null))!.replyCount).toBe(2);

    expect(await code(b.editComment("b", top.id, "x"))).toBe("denied");
    await b.deleteComment("a", top.id);
    const afterParent = await b.listComments(post.id);
    expect(afterParent.find((c) => c.id === top.id)).toMatchObject({ deleted: true, body: "" });
    expect(await code(b.addComment("c", post.id, top.id, "late"))).toBe("invalid");

    await b.deleteComment("b", reply.id);
    expect(await b.listComments(post.id)).toEqual([]);
  });

  it("follows and filters the Following timeline", async () => {
    await b.createPost("a", "from a");
    await b.createPost("b", "from b");
    await b.toggleFollow("me", "a");
    expect((await b.listPosts({ viewerId: "me", following: true })).map((p) => p.body)).toEqual(["from a"]);
    expect(await b.followStats("a", "me")).toEqual({ followers: 1, following: 0, followedByMe: true });
    expect(await code(b.toggleFollow("me", "me"))).toBe("invalid");
  });
});
