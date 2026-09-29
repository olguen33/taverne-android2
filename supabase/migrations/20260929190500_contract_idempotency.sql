-- The phone keeps this UUID while retrying a publication after a lost response.
create function public.publish_contract_once(p_id uuid,p_title text,p_description text,p_reward bigint,
  p_danger integer,p_places integer,p_x real,p_y real,p_days integer[],p_start integer,p_end integer)
returns uuid language plpgsql security invoker set search_path=public,pg_temp as $$
declare v_day integer; v_owner uuid;
begin
  if auth.uid() is null or p_id is null then raise exception 'Connexion nécessaire'; end if;
  select proposer_id into v_owner from public.contracts where id=p_id;
  if found then
    if v_owner is distinct from auth.uid() then raise exception 'Identifiant déjà utilisé'; end if;
    return p_id;
  end if;
  if length(trim(coalesce(p_title,''))) not between 1 and 120
    or length(trim(coalesce(p_description,''))) not between 1 and 4000
    or p_reward not between 1 and 1000000000 or p_danger not between 1 and 5
    or p_places not between 1 and 12 or p_start not between 0 and 1439
    or p_end not between 0 and 1439 or p_start=p_end
    or coalesce(array_length(p_days,1),0) not between 1 and 7
    or p_x not between 0 and 1 or p_y not between 0 and 1
    or exists(select 1 from unnest(p_days) d where d not between 0 and 6)
    then raise exception 'Contrat invalide'; end if;
  insert into public.contracts(id,proposer_id,title,description,reward_text,reward_gold,danger,places,map_x,map_y)
    values(p_id,auth.uid(),trim(p_title),trim(p_description),p_reward::text||' po',p_reward,p_danger,p_places,p_x,p_y)
    on conflict (id) do nothing;
  if not found then
    select proposer_id into v_owner from public.contracts where id=p_id;
    if v_owner is distinct from auth.uid() then raise exception 'Identifiant déjà utilisé'; end if;
    return p_id;
  end if;
  for v_day in select distinct unnest(p_days) loop
    insert into public.slots(contract_id,weekday,start_minute,end_minute)
      values(p_id,v_day,p_start,p_end);
  end loop;
  return p_id;
end $$;
revoke all on function public.publish_contract_once(uuid,text,text,bigint,integer,integer,real,real,integer[],integer,integer) from public,anon;
grant execute on function public.publish_contract_once(uuid,text,text,bigint,integer,integer,real,real,integer[],integer,integer) to authenticated;
