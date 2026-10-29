"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Camera, ImagePlus, Trash2 } from "lucide-react";
import { ImageError, cropImage } from "@/lib/web/image";
import { BIO_MAX, NAME_MAX } from "@/lib/web/rules";
import type { Profile } from "@/lib/web/types";
import { Avatar, Dialog } from "./ui";

const length = (s: string) => Array.from(s.trim()).length;

/** undefined: unchanged; null: remove; Blob: new (cropped) image. */
export type ImageChange = Blob | null | undefined;
export type ProfileChanges = { displayName: string; bio: string; avatar: ImageChange; banner: ImageChange };

function usePreview(change: ImageChange, current: string | null): string | null {
  const objectUrl = useMemo(() => (change instanceof Blob ? URL.createObjectURL(change) : null), [change]);
  useEffect(() => () => {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }, [objectUrl]);
  if (change === null) return null;
  return objectUrl ?? current;
}

/** Photo, banner, name and bio for the signed-in user. The same profile shows in the Android app. */
export function EditProfileDialog({ profile, onSave, onClose }: {
  profile: Profile; onSave: (changes: ProfileChanges) => Promise<boolean>; onClose: () => void;
}) {
  const [name, setName] = useState(profile.displayName);
  const [bio, setBio] = useState(profile.bio);
  const [avatar, setAvatar] = useState<ImageChange>(undefined);
  const [banner, setBanner] = useState<ImageChange>(undefined);
  const [imageError, setImageError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const avatarInput = useRef<HTMLInputElement>(null);
  const bannerInput = useRef<HTMLInputElement>(null);
  const avatarPreview = usePreview(avatar, profile.avatarUrl);
  const bannerPreview = usePreview(banner, profile.bannerUrl);

  const nameError = length(name) < 2 ? "Use at least 2 characters." : length(name) > NAME_MAX ? `Use at most ${NAME_MAX} characters.` : null;
  const bioError = length(bio) > BIO_MAX ? `Use at most ${BIO_MAX} characters.` : null;
  const changed = name.trim() !== profile.displayName || bio.trim() !== profile.bio || avatar !== undefined || banner !== undefined;

  async function pick(file: File | undefined, kind: "avatar" | "banner") {
    if (!file) return;
    setImageError(null);
    try {
      const cropped = await cropImage(file, kind);
      if (kind === "avatar") setAvatar(cropped); else setBanner(cropped);
    } catch (e) {
      setImageError(e instanceof ImageError ? e.message : "That image couldn't be used.");
    }
  }

  return (
    <Dialog title="Edit profile" onClose={onClose}>
      <div className="w-edit-media">
        <div className="w-edit-banner" style={bannerPreview ? { backgroundImage: `url("${bannerPreview}")` } : undefined}>
          <div className="w-edit-banner-actions">
            <button type="button" className="w-media-btn" aria-label="Change banner" onClick={() => bannerInput.current?.click()}>
              <ImagePlus size={18} />
            </button>
            {bannerPreview && (
              <button type="button" className="w-media-btn" aria-label="Remove banner" onClick={() => setBanner(null)}>
                <Trash2 size={18} />
              </button>
            )}
          </div>
        </div>
        <div className="w-edit-avatar">
          <Avatar profile={{ ...profile, avatarUrl: avatarPreview }} id={profile.id} size={88} />
          <button type="button" className="w-media-btn w-media-btn-center" aria-label="Change profile photo"
            onClick={() => avatarInput.current?.click()}>
            <Camera size={18} />
          </button>
        </div>
        {avatarPreview && (
          <button type="button" className="w-link w-small w-remove-photo" onClick={() => setAvatar(null)}>Remove photo</button>
        )}
        <input ref={avatarInput} type="file" accept="image/*" hidden
          onChange={(e) => { void pick(e.target.files?.[0], "avatar"); e.target.value = ""; }} />
        <input ref={bannerInput} type="file" accept="image/*" hidden
          onChange={(e) => { void pick(e.target.files?.[0], "banner"); e.target.value = ""; }} />
      </div>
      {imageError && <p className="w-alert" role="alert">{imageError}</p>}

      <div className="w-field">
        <label htmlFor="profile-name">Name</label>
        <div className={`w-input-wrap${nameError ? " invalid" : ""}`}>
          <input id="profile-name" value={name} autoComplete="name" maxLength={NAME_MAX + 10}
            aria-invalid={nameError ? true : undefined} onChange={(e) => setName(e.target.value)} />
        </div>
        <p className={nameError ? "w-error" : "w-muted w-small"}>{nameError ?? `${length(name)}/${NAME_MAX}`}</p>
      </div>
      <div className="w-field">
        <label htmlFor="profile-bio">Bio</label>
        <textarea id="profile-bio" className="w-edit-area" rows={3} value={bio} placeholder="What are you working on?"
          aria-invalid={bioError ? true : undefined} onChange={(e) => setBio(e.target.value)} />
        <p className={bioError ? "w-error" : "w-muted w-small"}>{bioError ?? `${length(bio)}/${BIO_MAX}`}</p>
      </div>
      <p className="w-muted w-small">Your @{profile.handle.replace(/^@/, "")} handle stays the same.</p>
      <div className="w-dialog-actions">
        <button className="w-btn w-btn-ghost" onClick={onClose}>Cancel</button>
        <button className="w-btn w-btn-primary" disabled={busy || !!nameError || !!bioError || !changed}
          onClick={async () => {
            setBusy(true);
            const ok = await onSave({ displayName: name, bio, avatar, banner });
            setBusy(false);
            if (ok) onClose();
          }}>{busy ? "Saving…" : "Save"}</button>
      </div>
    </Dialog>
  );
}
