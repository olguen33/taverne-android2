create function public.save_character(p_id uuid,p_name text,p_race text,p_archetype text,p_origin text,
 p_sheet text,p_lore text,p_inventory text,p_notes text) returns uuid
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid;
begin
 if auth.uid() is null then raise exception 'Connexion nécessaire'; end if;
 if length(trim(coalesce(p_name,''))) not between 1 and 100 then raise exception 'Nom invalide'; end if;
 if p_id is null then
  insert into public.characters(owner_id,name,race,archetype,origin,sheet,lore,inventory,campaign_notes)
  values(auth.uid(),trim(p_name),coalesce(p_race,''),coalesce(p_archetype,''),coalesce(p_origin,''),
    coalesce(p_sheet,''),coalesce(p_lore,''),coalesce(p_inventory,''),coalesce(p_notes,'')) returning id into v_id;
 else
  update public.characters set name=trim(p_name),race=coalesce(p_race,''),archetype=coalesce(p_archetype,''),
    origin=coalesce(p_origin,''),sheet=coalesce(p_sheet,''),lore=coalesce(p_lore,''),
    inventory=coalesce(p_inventory,''),campaign_notes=coalesce(p_notes,'')
  where id=p_id and owner_id=auth.uid() returning id into v_id;
  if v_id is null then raise exception 'Personnage non autorisé'; end if;
 end if;
 return v_id;
end $$;
revoke all on function public.save_character(uuid,text,text,text,text,text,text,text,text) from public,anon;
grant execute on function public.save_character(uuid,text,text,text,text,text,text,text,text) to authenticated;

create function public.set_pseudo(p_pseudo text) returns void
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if auth.uid() is null or length(trim(coalesce(p_pseudo,''))) not between 3 and 32 then raise exception 'Pseudo invalide'; end if;
 update public.profiles set pseudo=trim(p_pseudo) where id=auth.uid();
end $$;
revoke all on function public.set_pseudo(text) from public,anon;
grant execute on function public.set_pseudo(text) to authenticated;

create function public.edit_contract(p_id uuid,p_title text,p_description text,p_reward bigint,
 p_danger integer,p_places integer,p_x real,p_y real,p_days integer[],p_start integer,p_end integer)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare v_contract public.contracts%rowtype; v_day integer;
begin
 select * into v_contract from public.contracts where id=p_id for update;
 if v_contract.proposer_id is distinct from auth.uid() or v_contract.status<>'ouvert' or v_contract.paid_out then raise exception 'Contrat non modifiable'; end if;
 if length(trim(coalesce(p_title,''))) not between 1 and 120 or length(trim(coalesce(p_description,''))) not between 1 and 4000
   or p_reward not between 1 and 1000000000 or p_danger not between 1 and 5 or p_places not between 1 and 12
   or p_start not between 0 and 1439 or p_end not between 0 and 1439 or p_start=p_end
   or coalesce(array_length(p_days,1),0) not between 1 and 7
   or exists(select 1 from unnest(p_days) d where d not between 0 and 6) then raise exception 'Contrat invalide'; end if;
 update public.contracts set title=trim(p_title),description=trim(p_description),reward_gold=p_reward,
   reward_text=p_reward::text||' po',danger=p_danger,places=p_places,map_x=p_x,map_y=p_y where id=p_id;
 delete from public.slots where contract_id=p_id and not (weekday=any(p_days) and start_minute=p_start and end_minute=p_end);
 for v_day in select distinct unnest(p_days) loop
   insert into public.slots(contract_id,weekday,start_minute,end_minute) values(p_id,v_day,p_start,p_end) on conflict do nothing;
 end loop;
end $$;
revoke all on function public.edit_contract(uuid,text,text,bigint,integer,integer,real,real,integer[],integer,integer) from public,anon;
grant execute on function public.edit_contract(uuid,text,text,bigint,integer,integer,real,real,integer[],integer,integer) to authenticated;
