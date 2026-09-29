-- Public display names and shared rumours. Private character sheets stay owner-only.
create function public.shared_character_names()
returns table(id uuid, name text, owner_id uuid)
language sql stable security definer set search_path=public,pg_temp as $$
  select c.id,c.name,c.owner_id from public.characters c
$$;
revoke all on function public.shared_character_names() from public,anon;
grant execute on function public.shared_character_names() to authenticated;

create table public.rumours (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id),
  description text not null check (length(description) between 1 and 2000),
  map_x real not null check (map_x between 0 and 1),
  map_y real not null check (map_y between 0 and 1)
);
alter table public.rumours enable row level security;
revoke all on public.rumours from public,anon,authenticated;
grant select,insert(id,owner_id,description,map_x,map_y),update(description,map_x,map_y),delete on public.rumours to authenticated;
create policy rumours_read on public.rumours for select to authenticated using (true);
create policy rumours_insert on public.rumours for insert to authenticated with check (owner_id=auth.uid());
create policy rumours_update on public.rumours for update to authenticated using (owner_id=auth.uid()) with check (owner_id=auth.uid());
create policy rumours_delete on public.rumours for delete to authenticated using (owner_id=auth.uid());

-- Publish contract and proposed weekdays as one transaction.
create function public.publish_contract(p_title text,p_description text,p_reward bigint,
  p_danger integer,p_places integer,p_x real,p_y real,p_days integer[],p_start integer,p_end integer)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid; v_day integer;
begin
  if auth.uid() is null then raise exception 'Connexion nécessaire'; end if;
  if length(trim(coalesce(p_title,''))) not between 1 and 120
    or length(trim(coalesce(p_description,''))) not between 1 and 4000
    or p_reward not between 1 and 1000000000 or p_danger not between 1 and 5
    or p_places not between 1 and 12 or p_start not between 0 and 1439
    or p_end not between 0 and 1439 or p_start=p_end
    or coalesce(array_length(p_days,1),0) not between 1 and 7
    or (p_x is not null and (p_x<0 or p_x>1))
    or (p_y is not null and (p_y<0 or p_y>1)) then raise exception 'Contrat invalide'; end if;
  if exists(select 1 from unnest(p_days) d where d not between 0 and 6) then raise exception 'Jour invalide'; end if;
  insert into public.contracts(proposer_id,title,description,reward_text,reward_gold,danger,places,map_x,map_y)
    values(auth.uid(),trim(p_title),trim(p_description),p_reward::text||' po',p_reward,p_danger,p_places,p_x,p_y)
    returning id into v_id;
  for v_day in select distinct unnest(p_days) loop
    insert into public.slots(contract_id,weekday,start_minute,end_minute) values(v_id,v_day,p_start,p_end);
  end loop;
  return v_id;
end $$;
revoke all on function public.publish_contract(text,text,bigint,integer,integer,real,real,integer[],integer,integer) from public,anon;
grant execute on function public.publish_contract(text,text,bigint,integer,integer,real,real,integer[],integer,integer) to authenticated;
