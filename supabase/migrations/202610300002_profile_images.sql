-- Profile photo and banner, shared by the website and the app.
-- Images live in a public Storage bucket (anyone can view them, like the public timeline); each
-- signed-in user may only write inside their own folder "<user id>/...". Run after
-- 202610270001_create_profiles_follows_public_read.sql.

alter table public.profiles add column if not exists avatar_url text check (avatar_url is null or char_length(avatar_url) <= 600);
alter table public.profiles add column if not exists banner_url text check (banner_url is null or char_length(banner_url) <= 600);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('profile-media', 'profile-media', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create policy "Users read their own profile media"
on storage.objects for select to authenticated
using (bucket_id = 'profile-media' and (storage.foldername(name))[1] = auth.jwt()->>'sub');

create policy "Users upload their own profile media"
on storage.objects for insert to authenticated
with check (bucket_id = 'profile-media' and (storage.foldername(name))[1] = auth.jwt()->>'sub');

create policy "Users replace their own profile media"
on storage.objects for update to authenticated
using (bucket_id = 'profile-media' and (storage.foldername(name))[1] = auth.jwt()->>'sub')
with check (bucket_id = 'profile-media' and (storage.foldername(name))[1] = auth.jwt()->>'sub');

create policy "Users delete their own profile media"
on storage.objects for delete to authenticated
using (bucket_id = 'profile-media' and (storage.foldername(name))[1] = auth.jwt()->>'sub');
