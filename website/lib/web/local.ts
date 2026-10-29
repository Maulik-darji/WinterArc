import { AuthError, FeedError, type AuthApi, type Backend, type TimelineQuery } from "./backend";
import { BIO_MAX, COMMENT_MAX, NAME_MAX, POST_MAX, clean } from "./rules";
import { handleFromAccount } from "./handles";
import { normalizeEmail } from "./credentials";
import type { AuthUser, FollowStats, Post, PostComment, Profile } from "./types";

/**
 * Browser-only backend for development and tests: everything lives in localStorage, passwords are
 * SHA-256 hashed, and "Continue with Google" signs in a fake test account. Never used in
 * production builds unless NEXT_PUBLIC_ALLOW_LOCAL=true. Mirrors the Android debug test login.
 */
export type StorageLike = Pick<Storage, "getItem" | "setItem">;

type LocalUser = { uid: string; email: string; displayName: string; passwordHash: string };
type PostRow = { id: string; authorId: string; body: string; createdAt: string; editedAt: string | null };
type Db = {
  users: LocalUser[];
  session: string | null;
  profiles: Profile[];
  posts: PostRow[];
  likes: { postId: string; userId: string }[];
  comments: PostComment[];
  follows: { follower: string; followed: string }[];
};

const KEY = "winterarc.web.local.v1";
const GOOGLE_EMAIL = "google.test@example.test";

async function sha256(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Profiles saved before images existed have no image fields. */
const withImages = (p: Profile): Profile => ({ ...p, avatarUrl: p.avatarUrl ?? null, bannerUrl: p.bannerUrl ?? null });

async function blobToDataUrl(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return `data:${blob.type || "image/jpeg"};base64,${btoa(binary)}`;
}

export function createLocalBackend(
  storage: StorageLike,
  options: { now?: () => Date; id?: () => string } = {},
): Backend {
  const now = options.now ?? (() => new Date());
  const newId = options.id ?? (() => crypto.randomUUID());
  const listeners = new Set<() => void>();
  const authListeners = new Set<(u: AuthUser | null) => void>();

  const empty = (): Db => ({ users: [], session: null, profiles: [], posts: [], likes: [], comments: [], follows: [] });
  const load = (): Db => {
    try {
      const raw = storage.getItem(KEY);
      return raw ? { ...empty(), ...(JSON.parse(raw) as Partial<Db>) } : empty();
    } catch {
      return empty();
    }
  };
  const save = (db: Db) => storage.setItem(KEY, JSON.stringify(db));
  const emit = () => listeners.forEach((l) => l());
  const write = <T>(fn: (db: Db) => T): T => {
    const db = load();
    const result = fn(db);
    save(db);
    emit();
    return result;
  };
  const toAuthUser = (u: LocalUser): AuthUser => ({ uid: u.uid, email: u.email, displayName: u.displayName, emailVerified: false });
  const currentUser = (): AuthUser | null => {
    const db = load();
    const u = db.users.find((x) => x.uid === db.session);
    return u ? toAuthUser(u) : null;
  };
  const setSession = (uid: string | null) => {
    const db = load();
    db.session = uid;
    save(db);
    const user = currentUser();
    authListeners.forEach((l) => l(user));
  };

  if (typeof window !== "undefined") {
    window.addEventListener("storage", (e) => {
      if (e.key !== KEY) return;
      emit();
      const user = currentUser();
      authListeners.forEach((l) => l(user));
    });
  }

  const auth: AuthApi = {
    current: currentUser,
    whenReady: () => Promise.resolve(),
    onChange(listener) {
      authListeners.add(listener);
      return () => authListeners.delete(listener);
    },
    async signUp(name, email, password) {
      const e = normalizeEmail(email);
      const db = load();
      if (db.users.some((u) => u.email === e)) throw new AuthError("EMAIL_IN_USE");
      const user: LocalUser = { uid: newId(), email: e, displayName: name.trim(), passwordHash: await sha256(password) };
      db.users.push(user);
      db.session = user.uid;
      save(db);
      const result = toAuthUser(user);
      authListeners.forEach((l) => l(result));
      return result;
    },
    async signIn(email, password) {
      const e = normalizeEmail(email);
      const user = load().users.find((u) => u.email === e);
      if (!user || user.passwordHash !== (await sha256(password))) throw new AuthError("WRONG_CREDENTIALS");
      setSession(user.uid);
      return toAuthUser(user);
    },
    async signInWithGoogle() {
      const db = load();
      let user = db.users.find((u) => u.email === GOOGLE_EMAIL);
      if (!user) {
        user = { uid: newId(), email: GOOGLE_EMAIL, displayName: "Test Google account", passwordHash: "" };
        db.users.push(user);
        save(db);
      }
      setSession(user.uid);
      return toAuthUser(user);
    },
    async updatePassword(password) {
      const db = load();
      const user = db.users.find((u) => u.uid === db.session);
      if (!user) throw new AuthError("WRONG_CREDENTIALS");
      user.passwordHash = await sha256(password);
      save(db);
    },
    async sendPasswordReset(email) {
      if (!email.trim()) throw new AuthError("INVALID_EMAIL");
    },
    async signOut() {
      setSession(null);
    },
    testHint: "Test mode: accounts are stored in this browser only.",
  };

  const toPost = (db: Db, row: PostRow, viewerId: string | null): Post => ({
    ...row,
    likeCount: db.likes.filter((l) => l.postId === row.id).length,
    likedByMe: viewerId != null && db.likes.some((l) => l.postId === row.id && l.userId === viewerId),
    replyCount: db.comments.filter((c) => c.postId === row.id && !c.deleted).length,
  });
  const childCount = (db: Db, id: string) => db.comments.filter((c) => c.parentId === id).length;

  return {
    kind: "local",
    auth,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    async listPosts({ viewerId, following, authorId }: TimelineQuery) {
      const db = load();
      let rows = db.posts;
      if (authorId) rows = rows.filter((p) => p.authorId === authorId);
      if (following) {
        const ids = new Set(db.follows.filter((f) => f.follower === viewerId).map((f) => f.followed));
        rows = rows.filter((p) => ids.has(p.authorId));
      }
      return [...rows]
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : 0))
        .map((p) => toPost(db, p, viewerId));
    },
    async getPost(postId, viewerId) {
      const db = load();
      const row = db.posts.find((p) => p.id === postId);
      return row ? toPost(db, row, viewerId) : null;
    },
    async createPost(actorId, body) {
      const text = clean(body, POST_MAX);
      if (!text || !actorId) throw new FeedError("invalid");
      write((db) => db.posts.push({ id: newId(), authorId: actorId, body: text, createdAt: now().toISOString(), editedAt: null }));
    },
    async editPost(actorId, postId, body) {
      const text = clean(body, POST_MAX);
      if (!text) throw new FeedError("invalid");
      write((db) => {
        const row = db.posts.find((p) => p.id === postId);
        if (!row) throw new FeedError("not_found");
        if (row.authorId !== actorId) throw new FeedError("denied");
        if (row.body !== text) {
          row.body = text;
          row.editedAt = now().toISOString();
        }
      });
    },
    async deletePost(actorId, postId) {
      write((db) => {
        const row = db.posts.find((p) => p.id === postId);
        if (!row) throw new FeedError("not_found");
        if (row.authorId !== actorId) throw new FeedError("denied");
        db.posts = db.posts.filter((p) => p.id !== postId);
        db.likes = db.likes.filter((l) => l.postId !== postId);
        db.comments = db.comments.filter((c) => c.postId !== postId);
      });
    },
    async toggleLike(actorId, postId) {
      write((db) => {
        if (!db.posts.some((p) => p.id === postId)) throw new FeedError("not_found");
        const had = db.likes.some((l) => l.postId === postId && l.userId === actorId);
        db.likes = had
          ? db.likes.filter((l) => !(l.postId === postId && l.userId === actorId))
          : [...db.likes, { postId, userId: actorId }];
      });
    },

    async listComments(postId) {
      return load().comments.filter((c) => c.postId === postId);
    },
    async addComment(actorId, postId, parentId, body) {
      const text = clean(body, COMMENT_MAX);
      if (!text || !actorId) throw new FeedError("invalid");
      write((db) => {
        if (!db.posts.some((p) => p.id === postId)) throw new FeedError("not_found");
        if (parentId != null) {
          const parent = db.comments.find((c) => c.id === parentId);
          if (!parent || parent.postId !== postId) throw new FeedError("not_found");
          if (parent.deleted) throw new FeedError("invalid");
        }
        db.comments.push({
          id: newId(), postId, parentId, authorId: actorId, body: text,
          createdAt: now().toISOString(), editedAt: null, deleted: false,
        });
      });
    },
    async editComment(actorId, commentId, body) {
      const text = clean(body, COMMENT_MAX);
      if (!text) throw new FeedError("invalid");
      write((db) => {
        const c = db.comments.find((x) => x.id === commentId);
        if (!c || c.deleted) throw new FeedError("not_found");
        if (c.authorId !== actorId) throw new FeedError("denied");
        if (c.body !== text) {
          c.body = text;
          c.editedAt = now().toISOString();
        }
      });
    },
    async deleteComment(actorId, commentId) {
      write((db) => {
        const c = db.comments.find((x) => x.id === commentId);
        if (!c || c.deleted) throw new FeedError("not_found");
        if (c.authorId !== actorId) throw new FeedError("denied");
        if (childCount(db, commentId) > 0) {
          c.deleted = true;
          c.body = "";
          return;
        }
        let parentId = c.parentId;
        db.comments = db.comments.filter((x) => x.id !== commentId);
        while (parentId != null) {
          const parent = db.comments.find((x) => x.id === parentId);
          if (!parent || !parent.deleted || childCount(db, parentId) > 0) break;
          parentId = parent.parentId;
          db.comments = db.comments.filter((x) => x.id !== parent.id);
        }
      });
    },

    async ensureProfile(user) {
      const db = load();
      if (db.profiles.some((p) => p.id === user.uid)) return;
      const name = user.displayName?.trim() || user.email?.split("@")[0] || "WinterArc member";
      write((d) => d.profiles.push({
        id: user.uid, displayName: name, handle: handleFromAccount(user.email, name), bio: "", avatarUrl: null, bannerUrl: null,
      }));
    },
    async updateProfile(actorId, { displayName, bio }) {
      const name = clean(displayName, NAME_MAX);
      const about = bio.trim();
      if (!name || Array.from(name).length < 2 || Array.from(about).length > BIO_MAX) throw new FeedError("invalid");
      write((db) => {
        const p = db.profiles.find((x) => x.id === actorId);
        if (!p) throw new FeedError("not_found");
        p.displayName = name;
        p.bio = about;
      });
    },
    async setProfileImage(actorId, kind, image) {
      // The test backend keeps images in the browser as data URLs.
      const url = image ? await blobToDataUrl(image) : null;
      write((db) => {
        const p = db.profiles.find((x) => x.id === actorId);
        if (!p) throw new FeedError("not_found");
        if (kind === "avatar") p.avatarUrl = url;
        else p.bannerUrl = url;
      });
    },
    async getProfiles(ids) {
      const set = new Set(ids);
      return load().profiles.filter((p) => set.has(p.id)).map(withImages);
    },
    async suggestProfiles(viewerId, limit) {
      const db = load();
      const followed = new Set(db.follows.filter((f) => f.follower === viewerId).map((f) => f.followed));
      return db.profiles.filter((p) => p.id !== viewerId && !followed.has(p.id)).slice(0, limit).map(withImages);
    },
    async followStats(profileId, viewerId): Promise<FollowStats> {
      const { follows } = load();
      return {
        followers: follows.filter((f) => f.followed === profileId).length,
        following: follows.filter((f) => f.follower === profileId).length,
        followedByMe: viewerId != null && follows.some((f) => f.follower === viewerId && f.followed === profileId),
      };
    },
    async followingIds(viewerId) {
      return load().follows.filter((f) => f.follower === viewerId).map((f) => f.followed);
    },
    async toggleFollow(actorId, targetId) {
      if (actorId === targetId) throw new FeedError("invalid");
      write((db) => {
        const had = db.follows.some((f) => f.follower === actorId && f.followed === targetId);
        db.follows = had
          ? db.follows.filter((f) => !(f.follower === actorId && f.followed === targetId))
          : [...db.follows, { follower: actorId, followed: targetId }];
      });
    },
  };
}
