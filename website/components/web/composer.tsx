"use client";

import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { remaining } from "@/lib/web/rules";
import type { Profile } from "@/lib/web/types";
import { Avatar, Dialog } from "./ui";

const COUNTER_AT = 40;

function Ring({ left, max }: { left: number; max: number }) {
  const r = 9;
  const c = 2 * Math.PI * r;
  const used = Math.min(1, Math.max(0, (max - left) / max));
  const color = left < 0 ? "var(--w-danger)" : left <= 20 ? "#c47a08" : "var(--w-primary)";
  return (
    <span className="w-ring" role="img" aria-label={`${left} characters left`}>
      <svg width="24" height="24" viewBox="0 0 24 24">
        <circle cx="12" cy="12" r={r} fill="none" stroke="var(--w-border)" strokeWidth="2" />
        <circle cx="12" cy="12" r={r} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - used)} transform="rotate(-90 12 12)" />
      </svg>
      {left <= COUNTER_AT && <span style={{ color: left < 0 ? color : undefined }}>{left}</span>}
    </span>
  );
}

export function Composer({ me, userId, placeholder, submitLabel, max, onSubmit, replyingTo, onCancelReply, autoFocus, inputRef }: {
  me?: Profile; userId: string; placeholder: string; submitLabel: string; max: number;
  onSubmit: (text: string) => Promise<boolean>;
  replyingTo?: string | null; onCancelReply?: () => void; autoFocus?: boolean;
  inputRef?: React.RefObject<HTMLTextAreaElement | null>;
}) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const local = useRef<HTMLTextAreaElement>(null);
  const area = inputRef ?? local;
  const left = remaining(text, max);
  const valid = text.trim().length > 0 && left >= 0;

  useEffect(() => {
    const el = area.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${el.scrollHeight}px`;
  }, [text, area]);
  useEffect(() => { if (autoFocus) area.current?.focus(); }, [autoFocus, area]);

  async function send() {
    if (!valid || busy) return;
    setBusy(true);
    const ok = await onSubmit(text);
    setBusy(false);
    if (ok) setText("");
  }

  return (
    <div className="w-composer">
      <Avatar profile={me} id={userId} />
      <div className="w-composer-main">
        {replyingTo && (
          <div className="w-replying">
            <span>Replying to <b>{replyingTo}</b></span>
            {onCancelReply && <button type="button" className="w-icon-btn" aria-label="Cancel reply" onClick={onCancelReply}><X size={14} /></button>}
          </div>
        )}
        <textarea ref={area} rows={1} value={text} placeholder={placeholder} aria-label={placeholder}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => { if ((e.metaKey || e.ctrlKey) && e.key === "Enter") void send(); }} />
        <div className="w-composer-bar">
          <Ring left={left} max={max} />
          <button className="w-btn w-btn-primary" disabled={!valid || busy} onClick={() => void send()}>{submitLabel}</button>
        </div>
      </div>
    </div>
  );
}

/** Edit dialog for a post or comment. */
export function EditDialog({ title, initial, max, onSave, onClose }: {
  title: string; initial: string; max: number; onSave: (text: string) => Promise<boolean>; onClose: () => void;
}) {
  const [text, setText] = useState(initial);
  const [busy, setBusy] = useState(false);
  const left = remaining(text, max);
  const valid = text.trim().length > 0 && left >= 0 && text.trim() !== initial.trim();
  return (
    <Dialog title={title} onClose={onClose}>
      <textarea className="w-edit-area" rows={5} value={text} autoFocus aria-label={title} onChange={(e) => setText(e.target.value)} />
      <div className="w-dialog-actions">
        <Ring left={left} max={max} />
        <button className="w-btn w-btn-ghost" onClick={onClose}>Cancel</button>
        <button className="w-btn w-btn-primary" disabled={!valid || busy}
          onClick={async () => { setBusy(true); const ok = await onSave(text); setBusy(false); if (ok) onClose(); }}>Save</button>
      </div>
    </Dialog>
  );
}
