import { createClient, type SupabaseClient, type User } from "@supabase/supabase-js";
import { AuthError, FeedError, type AuthApi, type AuthErrorCode, type Backend, type TimelineQuery } from "./backend";
import { supabaseConfig } from "./config";
import { BIO_MAX, COMMENT_MAX, NAME_MAX, POST_MAX, clean } from "./rules";
import { handleFromAccount } from "./handles";
import type { AuthUser, FollowStats, Post, PostComment, Profile } from "./types";

/**
 * Production backend: Supabase Auth (email + password, Google) and Supabase data. Row-level
 * security keys on the session token's `sub`, which is the user's id. See supabase/migrations.
 */
const AUTH_CODES: Record<string, AuthErrorCode> = {
  user_already_exists: "EMAIL_IN_USE",
  email_exists: "EMAIL_IN_USE",
  email_address_invalid: "INVALID_EMAIL",
  weak_password: "WEAK_PASSWORD",
  invalid_credentials: "WRONG_CREDENTIALS",
  email_not_confirmed: "EMAIL_NOT_CONFIRMED",
  user_banned: "ACCOUNT_DISABLED",
  over_request_rate_limit: "TOO_MANY_REQUESTS",
  over_email_send_rate_limit: "TOO_MANY_REQUESTS",
  provider_disabled: "NOT_CONFIGURED",
  signup_disabled: "NOT_CONFIGURED",
};

type AuthFailure = { code?: string; status?: number; name?: string; message?: string };

function authError(e: unknown): AuthError {
  if (e instanceof AuthError) return e;
  const f = (e ?? {}) as AuthFailure;
  if (f.code && AUTH_CODES[f.code]) return new AuthError(AUTH_CODES[f.code]);
  if (f.status === 429) return new AuthError("TOO_MANY_REQUESTS");
  if (f.name === "AuthRetryableFetchError" || f.status === 0) return new AuthError("NETWORK");
  const text = (f.message ?? "").toLowerCase();
  if (text.includes("provider") && text.includes("not enabled")) return new AuthError("NOT_CONFIGURED");
  if (text.includes("invalid login credentials")) return new AuthError("WRONG_CREDENTIALS");
  return new AuthError("UNKNOWN");
}

const toAuthUser = (u: User): AuthUser => {
  const meta = (u.user_metadata ?? {}) as { full_name?: string; name?: string };
  return { uid: u.id, email: u.email ?? null, displayName: meta.full_name ?? meta.name ?? null, emailVerified: Boolean(u.email_confirmed_at) };
};

type DbError = { code?: string; message?: string; status?: number } | null;

function check(error: DbError): void {
  if (!error) return;
  if (error.code === "42501" || error.status === 401 || error.status === 403) throw new FeedError("denied");
  if (!error.code && /fetch|network|failed/i.test(error.message ?? "")) throw new FeedError("network");
  throw new FeedError("invalid");
}

type PostRow = {
  id: string; author_user_id: string; body: string; created_at: string; edited_at: string | null;
  feed_post_likes?: { count: number }[]; post_comments?: { count: number }[];
};
type CommentRow = {
  id: string; post_id: string; parent_comment_id: string | null; author_user_id: string; body: string;
  created_at: string; edited_at: string | null; deleted: boolean;
};
type ProfileRow = {
  user_id: string; display_name: string; handle: string; bio: string | null;
  avatar_url?: string | null; banner_url?: string | null;
};
// "*" rather than named columns: a project that hasn't run the profile-images migration yet has no
// avatar_url / banner_url columns, and naming them would make every profile (and the timeline) fail.
const PROFILE_SELECT = "*";
const MEDIA_BUCKET = "profile-media";

const POST_SELECT = "id, author_user_id, body, created_at, edited_at, feed_post_likes(count), post_comments(count)";

const toComment = (r: CommentRow): PostComment => ({
  id: r.id, postId: r.post_id, parentId: r.parent_comment_id, authorId: r.author_user_id, body: r.body,
  createdAt: r.created_at, editedAt: r.edited_at, deleted: r.deleted,
});
const toProfile = (r: ProfileRow): Profile => ({
  id: r.user_id, displayName: r.display_name, handle: r.handle, bio: r.bio ?? "",
  avatarUrl: r.avatar_url ?? null, bannerUrl: r.banner_url ?? null,
});

export function createCloudBackend(): Backend {
  const sb: SupabaseClient = createClient(supabaseConfig.url, supabaseConfig.key, {
    auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
  });

  const listeners = new Set<() => void>();
  const emit = () => listeners.forEach((l) => l());

  // Live updates: any change to the shared social tables (from this site, other browsers or the
  // Android app) is pushed by Supabase Realtime; screens refetch. Bursts are coalesced.
  let liveTimer: ReturnType<typeof setTimeout> | undefined;
  const scheduleEmit = () => {
    if (liveTimer) clearTimeout(liveTimer);
    liveTimer = setTimeout(emit, 120);
  };
  const live = sb.channel("winterarc-social");
  for (const table of ["feed_posts", "feed_post_likes", "post_comments", "profiles", "follows"]) {
    live.on("postgres_changes", { event: "*", schema: "public", table }, scheduleEmit);
  }
  live.subscribe((status) => {
    // After a dropped connection comes back, catch up on anything missed.
    if (status === "SUBSCRIBED") scheduleEmit();
  });

  let cached: AuthUser | null = null;
  const authListeners = new Set<(u: AuthUser | null) => void>();
  // Keep this callback synchronous: calling other Supabase methods inside it can deadlock.
  sb.auth.onAuthStateChange((_event, session) => {
    cached = session?.user ? toAuthUser(session.user) : null;
    authListeners.forEach((l) => l(cached));
  });
  const readyPromise = sb.auth.getSession().then(({ data }) => {
    cached = data.session?.user ? toAuthUser(data.session.user) : null;
  });

  const auth: AuthApi = {
    current: () => cached,
    whenReady: () => readyPromise,
    onChange(listener) {
      authListeners.add(listener);
      return () => authListeners.delete(listener);
    },
    async signUp(name, email, password) {
      const { data, error } = await sb.auth.signUp({
        email: email.trim(), password,
        options: { data: { full_name: name.trim() }, emailRedirectTo: `${window.location.origin}/login` },
      });
      if (error) throw authError(error);
      if (data.session && data.user) return toAuthUser(data.user);
      // Supabase hides whether an email is registered by returning a user with no identities.
      if (data.user && (data.user.identities ?? []).length === 0) throw new AuthError("EMAIL_IN_USE");
      return null;
    },
    async signIn(email, password) {
      const { data, error } = await sb.auth.signInWithPassword({ email: email.trim(), password });
      if (error || !data.user) throw authError(error);
      return toAuthUser(data.user);
    },
    async signInWithGoogle(next = "/home") {
      const { error } = await sb.auth.signInWithOAuth({
        provider: "google",
        options: { redirectTo: `${window.location.origin}${next}` },
      });
      if (error) throw authError(error);
      return null;
    },
    async sendPasswordReset(email) {
      const { error } = await sb.auth.resetPasswordForEmail(email.trim(), { redirectTo: `${window.location.origin}/reset-password` });
      if (error) throw authError(error);
    },
    async updatePassword(password) {
      const { error } = await sb.auth.updateUser({ password });
      if (error) throw authError(error);
    },
    async signOut() {
      await sb.auth.signOut();
    },
    testHint: null,
  };

  async function followingIds(viewerId: string): Promise<string[]> {
    const { data, error } = await sb.from("follows").select("followed_user_id").eq("follower_user_id", viewerId);
    check(error);
    return (data ?? []).map((r: { followed_user_id: string }) => r.followed_user_id);
  }

  async function likedSet(viewerId: string | null, postIds: string[]): Promise<Set<string>> {
    if (!viewerId || postIds.length === 0) return new Set();
    const { data, error } = await sb.from("feed_post_likes").select("post_id").eq("user_id", viewerId).in("post_id", postIds);
    check(error);
    return new Set((data ?? []).map((r: { post_id: string }) => r.post_id));
  }

  const toPost = (r: PostRow, liked: Set<string>): Post => ({
    id: r.id, authorId: r.author_user_id, body: r.body, createdAt: r.created_at, editedAt: r.edited_at,
    likeCount: r.feed_post_likes?.[0]?.count ?? 0,
    likedByMe: liked.has(r.id),
    replyCount: r.post_comments?.[0]?.count ?? 0,
  });

  const count = async (table: string, column: string, value: string): Promise<number> => {
    const { count: n, error } = await sb.from(table).select("*", { count: "exact", head: true }).eq(column, value);
    check(error);
    return n ?? 0;
  };

  return {
    kind: "cloud",
    auth,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    async listPosts({ viewerId, following, authorId }: TimelineQuery) {
      let query = sb.from("feed_posts").select(POST_SELECT).eq("post_comments.deleted", false)
        .order("created_at", { ascending: false }).limit(100);
      if (authorId) query = query.eq("author_user_id", authorId);
      if (following) {
        const ids = viewerId ? await followingIds(viewerId) : [];
        if (ids.length === 0) return [];
        query = query.in("author_user_id", ids);
      }
      const { data, error } = await query;
      check(error);
      const rows = (data ?? []) as unknown as PostRow[];
      const liked = await likedSet(viewerId, rows.map((r) => r.id));
      return rows.map((r) => toPost(r, liked));
    },
    async getPost(postId, viewerId) {
      const { data, error } = await sb.from("feed_posts").select(POST_SELECT).eq("post_comments.deleted", false).eq("id", postId).maybeSingle();
      check(error);
      if (!data) return null;
      return toPost(data as unknown as PostRow, await likedSet(viewerId, [postId]));
    },
    async createPost(actorId, body) {
      const text = clean(body, POST_MAX);
      if (!text || !actorId) throw new FeedError("invalid");
      check((await sb.from("feed_posts").insert({ id: crypto.randomUUID(), body: text })).error);
      emit();
    },
    async editPost(actorId, postId, body) {
      const text = clean(body, POST_MAX);
      if (!text) throw new FeedError("invalid");
      const { data, error } = await sb.from("feed_posts").update({ body: text }).eq("id", postId).eq("author_user_id", actorId).select("id");
      check(error);
      if (!data || data.length === 0) throw new FeedError("denied");
      emit();
    },
    async deletePost(actorId, postId) {
      const { data, error } = await sb.from("feed_posts").delete().eq("id", postId).eq("author_user_id", actorId).select("id");
      check(error);
      if (!data || data.length === 0) throw new FeedError("denied");
      emit();
    },
    async toggleLike(actorId, postId) {
      const { count: had, error } = await sb.from("feed_post_likes").select("*", { count: "exact", head: true })
        .eq("post_id", postId).eq("user_id", actorId);
      check(error);
      if (had) check((await sb.from("feed_post_likes").delete().eq("post_id", postId).eq("user_id", actorId)).error);
      else check((await sb.from("feed_post_likes").insert({ post_id: postId, user_id: actorId })).error);
      emit();
    },

    async listComments(postId) {
      const { data, error } = await sb.from("post_comments").select("*").eq("post_id", postId).order("created_at", { ascending: true });
      check(error);
      return ((data ?? []) as CommentRow[]).map(toComment);
    },
    async addComment(actorId, postId, parentId, body) {
      const text = clean(body, COMMENT_MAX);
      if (!text || !actorId) throw new FeedError("invalid");
      check((await sb.from("post_comments").insert({
        id: crypto.randomUUID(), post_id: postId, parent_comment_id: parentId, body: text,
      })).error);
      emit();
    },
    async editComment(actorId, commentId, body) {
      const text = clean(body, COMMENT_MAX);
      if (!text) throw new FeedError("invalid");
      const { data, error } = await sb.from("post_comments").update({ body: text })
        .eq("id", commentId).eq("author_user_id", actorId).eq("deleted", false).select("id");
      check(error);
      if (!data || data.length === 0) throw new FeedError("denied");
      emit();
    },
    async deleteComment(actorId, commentId) {
      const getRow = async (id: string): Promise<CommentRow | null> => {
        const { data, error } = await sb.from("post_comments").select("*").eq("id", id).maybeSingle();
        check(error);
        return data as CommentRow | null;
      };
      const row = await getRow(commentId);
      if (!row || row.deleted) throw new FeedError("not_found");
      if (row.author_user_id !== actorId) throw new FeedError("denied");
      if ((await count("post_comments", "parent_comment_id", commentId)) > 0) {
        check((await sb.from("post_comments").update({ deleted: true, body: "" }).eq("id", commentId)).error);
      } else {
        check((await sb.from("post_comments").delete().eq("id", commentId)).error);
        let parentId = row.parent_comment_id;
        while (parentId) {
          const parent = await getRow(parentId);
          if (!parent || !parent.deleted || (await count("post_comments", "parent_comment_id", parentId)) > 0) break;
          parentId = parent.parent_comment_id;
          // A tombstone can only be removed by its author's token, so this is best effort.
          if ((await sb.from("post_comments").delete().eq("id", parent.id)).error) break;
        }
      }
      emit();
    },

    async ensureProfile(user) {
      const name = user.displayName?.trim() || user.email?.split("@")[0] || "WinterArc member";
      const { error } = await sb.from("profiles").upsert(
        { user_id: user.uid, display_name: name, handle: handleFromAccount(user.email, name), bio: "" },
        { onConflict: "user_id", ignoreDuplicates: true },
      );
      check(error);
    },
    async updateProfile(actorId, { displayName, bio }) {
      const name = clean(displayName, NAME_MAX);
      const about = bio.trim();
      if (!name || Array.from(name).length < 2 || Array.from(about).length > BIO_MAX) throw new FeedError("invalid");
      const { data, error } = await sb.from("profiles").update({ display_name: name, bio: about })
        .eq("user_id", actorId).select("user_id");
      check(error);
      if (!data || data.length === 0) throw new FeedError("denied");
      emit();
    },
    async setProfileImage(actorId, kind, image) {
      let url: string | null = null;
      if (image) {
        // A new file name each time, so browsers and the app never show a cached old picture.
        const path = `${actorId}/${kind}-${Date.now()}.jpg`;
        const { error } = await sb.storage.from(MEDIA_BUCKET).upload(path, image, { contentType: "image/jpeg", upsert: false });
        if (error) throw new FeedError(/size|large/i.test(error.message) ? "invalid" : "network");
        url = sb.storage.from(MEDIA_BUCKET).getPublicUrl(path).data.publicUrl;
      }
      const column = kind === "avatar" ? "avatar_url" : "banner_url";
      const { data, error } = await sb.from("profiles").update({ [column]: url }).eq("user_id", actorId).select("user_id");
      check(error);
      if (!data || data.length === 0) throw new FeedError("denied");
      emit();
    },
    async getProfiles(ids) {
      if (ids.length === 0) return [];
      const { data, error } = await sb.from("profiles").select(PROFILE_SELECT).in("user_id", ids);
      check(error);
      return ((data ?? []) as ProfileRow[]).map(toProfile);
    },
    async suggestProfiles(viewerId, limit) {
      const { data, error } = await sb.from("profiles").select(PROFILE_SELECT)
        .order("created_at", { ascending: false }).limit(50);
      check(error);
      const followed = new Set(viewerId ? await followingIds(viewerId) : []);
      return ((data ?? []) as ProfileRow[]).map(toProfile).filter((p) => p.id !== viewerId && !followed.has(p.id)).slice(0, limit);
    },
    async followStats(profileId, viewerId): Promise<FollowStats> {
      const [followers, following, mine] = await Promise.all([
        count("follows", "followed_user_id", profileId),
        count("follows", "follower_user_id", profileId),
        viewerId
          ? sb.from("follows").select("*", { count: "exact", head: true }).eq("follower_user_id", viewerId).eq("followed_user_id", profileId)
          : Promise.resolve({ count: 0, error: null }),
      ]);
      check(mine.error);
      return { followers, following, followedByMe: (mine.count ?? 0) > 0 };
    },
    followingIds,
    async toggleFollow(actorId, targetId) {
      if (actorId === targetId) throw new FeedError("invalid");
      const { count: had, error } = await sb.from("follows").select("*", { count: "exact", head: true })
        .eq("follower_user_id", actorId).eq("followed_user_id", targetId);
      check(error);
      if (had) check((await sb.from("follows").delete().eq("follower_user_id", actorId).eq("followed_user_id", targetId)).error);
      else check((await sb.from("follows").insert({ followed_user_id: targetId })).error);
      emit();
    },
  };
}
