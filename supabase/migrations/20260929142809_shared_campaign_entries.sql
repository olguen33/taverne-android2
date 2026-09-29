-- Shared campaign notices live alongside private character campaign notes.
create table if not exists public.campaign_entries (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(id),
  title text not null check (length(trim(title)) between 1 and 120),
  body text not null check (length(trim(body)) between 1 and 8000),
  created_at timestamptz not null default now()
);

create index if not exists campaign_entries_created_idx on public.campaign_entries(created_at desc);
alter table public.campaign_entries enable row level security;
revoke all on public.campaign_entries from public, anon, authenticated;
grant select, delete on public.campaign_entries to authenticated;
grant insert (author_id,title,body) on public.campaign_entries to authenticated;
grant update (title,body) on public.campaign_entries to authenticated;

drop policy if exists campaign_entries_read on public.campaign_entries;
create policy campaign_entries_read on public.campaign_entries
  for select to authenticated using (true);
drop policy if exists campaign_entries_insert on public.campaign_entries;
create policy campaign_entries_insert on public.campaign_entries
  for insert to authenticated with check (author_id=(select auth.uid()));
drop policy if exists campaign_entries_update on public.campaign_entries;
create policy campaign_entries_update on public.campaign_entries
  for update to authenticated using (author_id=(select auth.uid()))
  with check (author_id=(select auth.uid()));
drop policy if exists campaign_entries_delete on public.campaign_entries;
create policy campaign_entries_delete on public.campaign_entries
  for delete to authenticated using (author_id=(select auth.uid()));
