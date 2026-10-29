-- Rows are owned by the signed-in Supabase user: user_id is the access token's subject (the user's
-- auth.users id, stored as text) and RLS compares it with auth.jwt()->>'sub'.
create table if not exists public.goals (
    uuid uuid primary key,
    user_id text not null default (auth.jwt()->>'sub'),
    title text not null,
    description text,
    activity_type text not null,
    tracking_mode text not null,
    unit text not null,
    custom_unit_label text,
    current_target_milli bigint not null check (current_target_milli >= 0),
    start_target_milli bigint,
    final_target_milli bigint,
    hop_milli bigint,
    schedule_mask integer not null check (schedule_mask between 0 and 127),
    start_date date not null,
    end_date date,
    reminder_enabled boolean not null default false,
    reminder_minute_of_day integer not null check (reminder_minute_of_day between 0 and 1439),
    color text not null,
    icon text not null,
    status text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists goals_user_updated_idx on public.goals (user_id, updated_at);

alter table public.goals enable row level security;

grant select, insert, update, delete on public.goals to authenticated;

create policy "Users manage their own goals"
on public.goals
for all
to authenticated
using (user_id = auth.jwt()->>'sub')
with check (user_id = auth.jwt()->>'sub');
