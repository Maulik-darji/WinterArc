"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { MoreHorizontal, Pencil, Trash2, X } from "lucide-react";
import { LOGO_BG, LOGO_FG, LOGO_PATH } from "@/lib/web/logo";
import type { Profile } from "@/lib/web/types";

export function Logo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" style={{ borderRadius: size * 0.22, flex: "none" }}>
      <rect width="100" height="100" fill={LOGO_BG} />
      <path d={LOGO_PATH} fill={LOGO_FG} />
    </svg>
  );
}

const TONES = ["#0f8a6a", "#6b55d9", "#c47a08", "#2d7fd3", "#c93f69", "#0f8690"];
const tone = (id: string) => TONES[Array.from(id).reduce((h, ch) => (h * 31 + ch.charCodeAt(0)) >>> 0, 7) % TONES.length];

export function Avatar({ profile, id, size = 40, muted = false }: { profile?: Profile; id: string; size?: number; muted?: boolean }) {
  if (!muted && profile?.avatarUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- user images come from Supabase Storage
      <img className="w-avatar w-avatar-img" src={profile.avatarUrl} alt="" aria-hidden="true" width={size} height={size}
        loading="lazy" decoding="async" style={{ width: size, height: size }} />
    );
  }
  const initial = profile ? Array.from(profile.displayName.trim())[0]?.toUpperCase() ?? "?" : "";
  return (
    <span className="w-avatar" aria-hidden="true"
      style={{ width: size, height: size, fontSize: size * 0.42, background: muted ? undefined : tone(id), opacity: muted ? 0.35 : 1 }}>
      {muted ? "" : initial}
    </span>
  );
}

export function ProfileLink({ id, className, children, label }: { id: string; className?: string; children: ReactNode; label?: string }) {
  return (
    <Link href={`/profile/${encodeURIComponent(id)}`} className={className} aria-label={label}
      onClick={(e) => e.stopPropagation()}>
      {children}
    </Link>
  );
}

/** Three-dot menu with Edit / Delete for content the viewer wrote. */
export function OwnerMenu({ label, onEdit, onDelete }: { label: string; onEdit: () => void; onDelete: () => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: Event) => {
      if (e instanceof KeyboardEvent ? e.key === "Escape" : !ref.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", close);
    };
  }, [open]);
  return (
    <div className="w-menu" ref={ref} onClick={(e) => e.stopPropagation()}>
      <button type="button" className="w-icon-btn" aria-label={label} aria-haspopup="menu" aria-expanded={open}
        onClick={() => setOpen((o) => !o)}>
        <MoreHorizontal size={18} />
      </button>
      {open && (
        <div className="w-menu-list" role="menu">
          <button role="menuitem" onClick={() => { setOpen(false); onEdit(); }}><Pencil size={16} /> Edit</button>
          <button role="menuitem" className="danger" onClick={() => { setOpen(false); onDelete(); }}><Trash2 size={16} /> Delete</button>
        </div>
      )}
    </div>
  );
}

/** A modal built on <dialog>: focus trap, Esc to close and backdrop come from the browser. */
export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
  }, []);
  return (
    <dialog ref={ref} className="w-dialog" aria-label={title} onClose={onClose}
      onClick={(e) => e.target === ref.current && ref.current?.close()}>
      <div className="w-dialog-body">
        <div className="w-dialog-head">
          <h2>{title}</h2>
          <button type="button" className="w-icon-btn" aria-label="Close" onClick={() => ref.current?.close()}><X size={18} /></button>
        </div>
        {children}
      </div>
    </dialog>
  );
}

export function ConfirmDialog({ title, body, action, onConfirm, onClose }: {
  title: string; body: string; action: string; onConfirm: () => void; onClose: () => void;
}) {
  return (
    <Dialog title={title} onClose={onClose}>
      <p className="w-muted">{body}</p>
      <div className="w-dialog-actions">
        <button className="w-btn w-btn-ghost" onClick={onClose}>Cancel</button>
        <button className="w-btn w-btn-danger" onClick={() => { onConfirm(); onClose(); }}>{action}</button>
      </div>
    </Dialog>
  );
}
