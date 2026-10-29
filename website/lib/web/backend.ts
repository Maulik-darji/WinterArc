import type { AuthUser, FollowStats, Post, PostComment, Profile, ProfileImageKind } from "./types";

export type AuthErrorCode =
  | "INVALID_EMAIL" | "EMAIL_IN_USE" | "WRONG_CREDENTIALS" | "WEAK_PASSWORD" | "ACCOUNT_DISABLED"
  | "TOO_MANY_REQUESTS" | "EMAIL_NOT_CONFIRMED" | "NETWORK" | "GOOGLE_CANCELLED" | "NOT_CONFIGURED" | "UNKNOWN";

export class AuthError extends Error {
  constructor(readonly code: AuthErrorCode) {
    super(code);
    this.name = "AuthError";
  }
}

export const AUTH_MESSAGES: Record<AuthErrorCode, string> = {
  INVALID_EMAIL: "Enter a valid email address.",
  EMAIL_IN_USE: "An account already exists for this email. Try logging in.",
  WRONG_CREDENTIALS: "Email or password is incorrect.",
  WEAK_PASSWORD: "Choose a stronger password: at least 8 characters with a letter and a number.",
  ACCOUNT_DISABLED: "This account has been disabled.",
  TOO_MANY_REQUESTS: "Too many attempts. Wait a few minutes and try again.",
  EMAIL_NOT_CONFIRMED: "Confirm your email address first: open the link we sent you, then log in.",
  NETWORK: "No connection. Check your internet and try again.",
  GOOGLE_CANCELLED: "Google sign-in was cancelled.",
  NOT_CONFIGURED: "Sign-in isn't available on this site yet.",
  UNKNOWN: "Something went wrong. Please try again.",
};

export type FeedErrorCode = "invalid" | "not_found" | "denied" | "network";

export class FeedError extends Error {
  constructor(readonly code: FeedErrorCode) {
    super(code);
    this.name = "FeedError";
  }
}

export const FEED_MESSAGES: Record<FeedErrorCode, string> = {
  invalid: "Write something first, within the character limit.",
  not_found: "That was deleted.",
  denied: "You can only change your own posts and comments.",
  network: "Couldn't reach the server. Try again.",
};

export interface AuthApi {
  current(): AuthUser | null;
  /** Resolves once the initial signed-in/out state is known. */
  whenReady(): Promise<void>;
  onChange(listener: (user: AuthUser | null) => void): () => void;
  /** Resolves null when the account must first be confirmed through the emailed link. */
  signUp(name: string, email: string, password: string): Promise<AuthUser | null>;
  signIn(email: string, password: string): Promise<AuthUser>;
  /** Resolves null when the browser is being redirected to Google (the page is about to unload). */
  signInWithGoogle(next?: string): Promise<AuthUser | null>;
  sendPasswordReset(email: string): Promise<void>;
  /** Sets a new password for the signed-in user (used after following the reset email link). */
  updatePassword(password: string): Promise<void>;
  signOut(): Promise<void>;
  /** Shown on the auth screens in test mode. */
  readonly testHint: string | null;
}

export type TimelineQuery = { viewerId: string | null; following?: boolean; authorId?: string };

export interface Backend {
  readonly kind: "local" | "cloud";
  readonly auth: AuthApi;
  /** Called after any write (this tab or another), so screens can refetch. */
  subscribe(listener: () => void): () => void;

  listPosts(query: TimelineQuery): Promise<Post[]>;
  getPost(postId: string, viewerId: string | null): Promise<Post | null>;
  createPost(actorId: string, body: string): Promise<void>;
  editPost(actorId: string, postId: string, body: string): Promise<void>;
  deletePost(actorId: string, postId: string): Promise<void>;
  toggleLike(actorId: string, postId: string): Promise<void>;

  listComments(postId: string): Promise<PostComment[]>;
  addComment(actorId: string, postId: string, parentId: string | null, body: string): Promise<void>;
  editComment(actorId: string, commentId: string, body: string): Promise<void>;
  deleteComment(actorId: string, commentId: string): Promise<void>;

  ensureProfile(user: AuthUser): Promise<void>;
  getProfiles(ids: string[]): Promise<Profile[]>;
  /** Changes the signed-in user's own name and bio (shared with the app). */
  updateProfile(actorId: string, changes: { displayName: string; bio: string }): Promise<void>;
  /** Uploads (JPEG, already cropped) or removes (null) the signed-in user's photo or banner. */
  setProfileImage(actorId: string, kind: ProfileImageKind, image: Blob | null): Promise<void>;
  suggestProfiles(viewerId: string | null, limit: number): Promise<Profile[]>;
  followStats(profileId: string, viewerId: string | null): Promise<FollowStats>;
  followingIds(viewerId: string): Promise<string[]>;
  toggleFollow(actorId: string, targetId: string): Promise<void>;
}
