export type Profile = {
  id: string;
  displayName: string;
  handle: string;
  bio: string;
  /** Public image URLs (shared with the app); null shows the initial / default banner. */
  avatarUrl: string | null;
  bannerUrl: string | null;
};

export type ProfileImageKind = "avatar" | "banner";

export type Post = {
  id: string;
  authorId: string;
  body: string;
  createdAt: string;
  editedAt: string | null;
  likeCount: number;
  likedByMe: boolean;
  replyCount: number;
};

/** A comment or reply. A deleted comment with replies stays as a tombstone (empty body). */
export type PostComment = {
  id: string;
  postId: string;
  parentId: string | null;
  authorId: string;
  body: string;
  createdAt: string;
  editedAt: string | null;
  deleted: boolean;
};

export type AuthUser = {
  uid: string;
  email: string | null;
  displayName: string | null;
  emailVerified: boolean;
};

export type FollowStats = { followers: number; following: number; followedByMe: boolean };
