-- Live updates: stream changes to the social tables through Supabase Realtime, so a post, like,
-- comment, edit, delete, profile change or follow shows up on the website and in the app within a
-- second, without refreshing. Realtime only delivers rows the subscriber may read (same RLS as
-- selects). Run after 202610270001_create_profiles_follows_public_read.sql.
do $$
declare
    t text;
begin
    foreach t in array array['feed_posts', 'feed_post_likes', 'post_comments', 'profiles', 'follows'] loop
        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
        ) then
            execute format('alter publication supabase_realtime add table public.%I', t);
        end if;
    end loop;
end $$;
