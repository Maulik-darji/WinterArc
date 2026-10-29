"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, useSyncExternalStore, type ReactNode } from "react";
import { Download, House, LogOut, Moon, PenLine, Sun, User, X } from "lucide-react";
import { APP_DOWNLOAD_URL } from "@/lib/web/config";
import { useQuery, useWeb } from "./providers";
import { useTheme } from "./theme";
import { Avatar, Logo, ProfileLink } from "./ui";

function Unavailable() {
  return (
    <div className="w-empty">
      <h2>WinterArc on the web isn&apos;t connected yet</h2>
      <p className="w-muted">The site needs its Supabase settings. See website/README.md.</p>
    </div>
  );
}

/** On Android phones, point visitors to the app: that is the best way to use WinterArc there. */
function AppBanner() {
  const android = useSyncExternalStore(() => () => undefined, () => /Android/i.test(navigator.userAgent), () => false);
  const [dismissed, setDismissed] = useState(() => {
    try { return sessionStorage.getItem("wa-banner") === "off"; } catch { return false; }
  });
  if (!android || dismissed) return null;
  return (
    <div className="w-banner">
      <Logo size={24} />
      <span>WinterArc works best in the app.</span>
      <a className="w-btn w-btn-primary w-btn-sm" href={APP_DOWNLOAD_URL} download>Get the app</a>
      <button className="w-icon-btn" aria-label="Dismiss" onClick={() => { setDismissed(true); try { sessionStorage.setItem("wa-banner", "off"); } catch { /* ignore */ } }}>
        <X size={16} />
      </button>
    </div>
  );
}

/** The signed-in person's own card: name, @handle, follower counts, link to their profile. */
function YourProfileCard() {
  const { user, me } = useWeb();
  const { data } = useQuery((b) => b.followStats(user!.uid, user!.uid), [user?.uid], Boolean(user));
  if (!user) return null;
  const name = me?.displayName ?? user.displayName ?? user.email?.split("@")[0] ?? "You";
  return (
    <section className="w-card w-me-card" aria-label="Your profile">
      <ProfileLink id={user.uid} className="w-me-head" label="Open your profile">
        <Avatar profile={me ?? undefined} id={user.uid} size={48} />
        <span className="w-me-text"><b>{name}</b><span className="w-muted">{me?.handle ?? ""}</span></span>
      </ProfileLink>
      {me?.bio && <p className="w-small">{me.bio}</p>}
      <p className="w-stats w-small">
        <span><b>{data?.following ?? 0}</b> Following</span>
        <span><b>{data?.followers ?? 0}</b> {data?.followers === 1 ? "Follower" : "Followers"}</span>
      </p>
      <ProfileLink id={user.uid} className="w-btn w-btn-outline w-btn-sm">View profile</ProfileLink>
    </section>
  );
}

function WhoToFollow() {
  const { user, backend, run, requireAuth } = useWeb();
  const { data } = useQuery((b) => b.suggestProfiles(user?.uid ?? null, 3), [user?.uid]);
  if (!data || data.length === 0) return null;
  return (
    <section className="w-card" aria-label="Who to follow">
      <h2>Who to follow</h2>
      {data.map((p) => (
        <div className="w-suggest" key={p.id}>
          <ProfileLink id={p.id} label={`View profile of ${p.displayName}`}><Avatar profile={p} id={p.id} /></ProfileLink>
          <ProfileLink id={p.id} className="w-suggest-name"><b>{p.displayName}</b><span className="w-muted">{p.handle}</span></ProfileLink>
          <button className="w-btn w-btn-dark w-btn-sm"
            onClick={() => requireAuth() && user && backend && void run(() => backend.toggleFollow(user.uid, p.id))}>Follow</button>
        </div>
      ))}
    </section>
  );
}

export function Shell({ children }: { children: ReactNode }) {
  const { status, user, me, backend } = useWeb();
  const { resolved, toggle } = useTheme();
  const pathname = usePathname();
  const router = useRouter();
  const profileHref = user ? `/profile/${encodeURIComponent(user.uid)}` : "/login";
  const items = [
    { href: "/home", label: "Home", icon: <House size={26} />, active: pathname === "/home" },
    { href: profileHref, label: "Profile", icon: <User size={26} />, active: pathname.startsWith("/profile") && user != null && pathname === profileHref },
  ];

  return (
    <div className="w-shell">
      <header className="w-left">
        <Link href="/" className="w-brand" aria-label="WinterArc, back to the website"><Logo size={34} /><span>WinterArc</span></Link>
        <nav aria-label="Main">
          {items.map((i) => (
            <Link key={i.label} href={i.href} className={`w-nav-item${i.active ? " active" : ""}`} aria-current={i.active ? "page" : undefined}>
              {i.icon}<span>{i.label}</span>
            </Link>
          ))}
          <button className="w-nav-item" onClick={toggle} aria-label={`Switch to ${resolved === "dark" ? "light" : "dark"} theme`}>
            {resolved === "dark" ? <Sun size={26} /> : <Moon size={26} />}<span>{resolved === "dark" ? "Light" : "Dark"} mode</span>
          </button>
        </nav>
        {user ? (
          <>
            <button className="w-btn w-btn-primary w-post-btn" onClick={() => router.push("/home#compose")} aria-label="Write a post"><PenLine size={20} className="w-post-icon" /><span className="w-post-label">Post</span></button>
            <div className="w-account">
              <Avatar profile={me ?? undefined} id={user.uid} />
              <div className="w-account-text"><b>{me?.displayName ?? user.displayName ?? "You"}</b><span className="w-muted">{me?.handle}</span></div>
              <button className="w-icon-btn" aria-label="Log out" onClick={() => backend?.auth.signOut().then(() => router.push("/"))}><LogOut size={18} /></button>
            </div>
          </>
        ) : status === "loading" ? null : (
          <div className="w-left-auth">
            <Link href="/signup" className="w-btn w-btn-primary">Sign up</Link>
            <Link href="/login" className="w-btn w-btn-outline">Log in</Link>
          </div>
        )}
      </header>

      <main className="w-center">
        <AppBanner />
        {status === "unavailable" ? <Unavailable /> : status === "loading" ? <div className="w-loading" aria-busy="true">Loading…</div> : children}
      </main>

      <aside className="w-right" aria-label="More">
        <section className="w-card w-app-card">
          <h2>Get the WinterArc app</h2>
          <p className="w-muted">Track your goals, check in daily and see your progress grid, on your phone.</p>
          <a className="w-btn w-btn-primary" href={APP_DOWNLOAD_URL} download><Download size={16} /> Download for Android</a>
        </section>
        {status === "ready" && user && <YourProfileCard />}
        {status === "ready" && <WhoToFollow />}
        <footer className="w-foot"><Link href="/">About WinterArc</Link><span>© 2026 WinterArc</span></footer>
      </aside>

      <nav className="w-bottom" aria-label="Main">
        {items.map((i) => (
          <Link key={i.label} href={i.href} className={i.active ? "active" : ""} aria-label={i.label} aria-current={i.active ? "page" : undefined}>{i.icon}</Link>
        ))}
        {user
          ? <button aria-label="Write a post" className="w-fab" onClick={() => router.push("/home#compose")}>+</button>
          : <Link href="/login" aria-label="Log in" className="w-fab">→</Link>}
      </nav>
    </div>
  );
}
