-- Profiles and follows for the social features, plus public (signed-out) read access so the website
-- can be browsed without an account, like a public timeline. Users are Supabase Auth users,
-- identified by the access token's subject (their id, as text). Run after 202610240001_create_feed.sql.

create table if not exists public.profiles (
    user_id text primary key default (auth.jwt()->>'sub'),
    display_name text not null check (char_length(btrim(display_name)) between 1 and 50),
    handle text not null check (handle ~ '^@[a-z0-9_]{1,15}$'),
    bio text not null default '' check (char_length(bio) <= 160),
    created_at timestamptz not null default now()
);

create index if not exists profiles_created_idx on public.profiles (created_at desc);

create table if not exists public.follows (
    follower_user_id text not null default (auth.jwt()->>'sub'),
    followed_user_id text not null,
    created_at timestamptz not null default now(),
    primary key (follower_user_id, followed_user_id),
    check (follower_user_id <> followed_user_id)
);

create index if not exists follows_followed_idx on public.follows (followed_user_id);

alter table public.profiles enable row level security;
alter table public.follows enable row level security;

grant select, insert, update on public.profiles to authenticated;
grant select, insert, delete on public.follows to authenticated;

create policy "Users create their own profile"
on public.profiles for insert to authenticated
with check (user_id = auth.jwt()->>'sub');

create policy "Users edit their own profile"
on public.profiles for update to authenticated
using (user_id = auth.jwt()->>'sub')
with check (user_id = auth.jwt()->>'sub');

create policy "Users follow as themselves"
on public.follows for insert to authenticated
with check (follower_user_id = auth.jwt()->>'sub');

create policy "Users unfollow as themselves"
on public.follows for delete to authenticated
using (follower_user_id = auth.jwt()->>'sub');

-- Everything below is world-readable: profiles, follows, posts, likes and comments.
grant select on public.profiles, public.follows, public.feed_posts, public.feed_post_likes, public.post_comments to anon;

create policy "Anyone can read profiles" on public.profiles for select to anon, authenticated using (true);
create policy "Anyone can read follows" on public.follows for select to anon, authenticated using (true);
create policy "Anyone can read posts" on public.feed_posts for select to anon using (true);
create policy "Anyone can read likes" on public.feed_post_likes for select to anon using (true);
create policy "Anyone can read comments" on public.post_comments for select to anon using (true);
