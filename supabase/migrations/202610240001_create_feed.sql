-- Feed: posts, likes and threaded comments. Mirrors the app's Room tables (SocialDatabase v2).
-- Users are Supabase Auth users, identified by the access token's subject (their id, as text), as in
-- 202610040001_create_goals.sql. Everyone signed in can read; only authors can change their content.

create table if not exists public.feed_posts (
    id uuid primary key,
    author_user_id text not null default (auth.jwt()->>'sub'),
    body text not null check (char_length(btrim(body)) between 1 and 280),
    created_at timestamptz not null default now(),
    edited_at timestamptz
);

create index if not exists feed_posts_created_idx on public.feed_posts (created_at desc);
create index if not exists feed_posts_author_idx on public.feed_posts (author_user_id, created_at desc);

create table if not exists public.feed_post_likes (
    post_id uuid not null references public.feed_posts (id) on delete cascade,
    user_id text not null default (auth.jwt()->>'sub'),
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create index if not exists feed_post_likes_user_idx on public.feed_post_likes (user_id);

-- A comment with replies is soft-deleted (deleted = true, body cleared) so the thread stays
-- readable; a comment without replies is hard-deleted.
create table if not exists public.post_comments (
    id uuid primary key,
    post_id uuid not null references public.feed_posts (id) on delete cascade,
    parent_comment_id uuid references public.post_comments (id) on delete cascade,
    author_user_id text not null default (auth.jwt()->>'sub'),
    body text not null,
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    deleted boolean not null default false,
    constraint post_comments_body_check check (
        (deleted and body = '') or (not deleted and char_length(btrim(body)) between 1 and 500)
    )
);

create index if not exists post_comments_post_idx on public.post_comments (post_id, created_at);
create index if not exists post_comments_parent_idx on public.post_comments (parent_comment_id);

-- Authorship and placement are fixed after creation; text edits stamp edited_at.
create or replace function public.feed_posts_guard_update()
returns trigger
language plpgsql
as $$
begin
    if new.id <> old.id or new.author_user_id <> old.author_user_id or new.created_at <> old.created_at then
        raise exception 'id, author and created_at cannot change';
    end if;
    if new.body <> old.body then
        new.edited_at := now();
    end if;
    return new;
end;
$$;

create or replace trigger feed_posts_guard_update
before update on public.feed_posts
for each row execute function public.feed_posts_guard_update();

create or replace function public.post_comments_guard_update()
returns trigger
language plpgsql
as $$
begin
    if new.id <> old.id or new.author_user_id <> old.author_user_id or new.created_at <> old.created_at
            or new.post_id <> old.post_id or new.parent_comment_id is distinct from old.parent_comment_id then
        raise exception 'a comment cannot change author, post, parent or creation time';
    end if;
    if old.deleted and not new.deleted then
        raise exception 'a deleted comment cannot be restored';
    end if;
    if new.deleted then
        new.body := '';
    elsif new.body <> old.body then
        new.edited_at := now();
    end if;
    return new;
end;
$$;

create or replace trigger post_comments_guard_update
before update on public.post_comments
for each row execute function public.post_comments_guard_update();

-- Row-level security

alter table public.feed_posts enable row level security;
alter table public.feed_post_likes enable row level security;
alter table public.post_comments enable row level security;

grant select, insert, update, delete on public.feed_posts to authenticated;
grant select, insert, delete on public.feed_post_likes to authenticated;
grant select, insert, update, delete on public.post_comments to authenticated;

create policy "Signed-in users read posts"
on public.feed_posts for select to authenticated
using (true);

create policy "Users create their own posts"
on public.feed_posts for insert to authenticated
with check (author_user_id = auth.jwt()->>'sub');

create policy "Authors edit their posts"
on public.feed_posts for update to authenticated
using (author_user_id = auth.jwt()->>'sub')
with check (author_user_id = auth.jwt()->>'sub');

create policy "Authors delete their posts"
on public.feed_posts for delete to authenticated
using (author_user_id = auth.jwt()->>'sub');

create policy "Signed-in users read likes"
on public.feed_post_likes for select to authenticated
using (true);

create policy "Users like as themselves"
on public.feed_post_likes for insert to authenticated
with check (user_id = auth.jwt()->>'sub');

create policy "Users remove their own likes"
on public.feed_post_likes for delete to authenticated
using (user_id = auth.jwt()->>'sub');

create policy "Signed-in users read comments"
on public.post_comments for select to authenticated
using (true);

-- Replies must target a live comment on the same post.
create policy "Users comment as themselves"
on public.post_comments for insert to authenticated
with check (
    author_user_id = auth.jwt()->>'sub'
    and not deleted
    and (
        parent_comment_id is null
        or exists (
            select 1 from public.post_comments parent
            where parent.id = post_comments.parent_comment_id
              and parent.post_id = post_comments.post_id
              and not parent.deleted
        )
    )
);

create policy "Authors edit or soft-delete their comments"
on public.post_comments for update to authenticated
using (author_user_id = auth.jwt()->>'sub')
with check (author_user_id = auth.jwt()->>'sub');

create policy "Authors delete their comments"
on public.post_comments for delete to authenticated
using (author_user_id = auth.jwt()->>'sub');
