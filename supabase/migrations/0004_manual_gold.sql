-- Preserve the existing game-master-managed purse field on character sheets.
create function public.set_character_gold(p_id uuid,p_gold bigint) returns void
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if p_gold not between 0 and 1000000000 then raise exception 'Bourse invalide'; end if;
 update public.characters set gold=p_gold where id=p_id and owner_id=auth.uid();
 if not found then raise exception 'Personnage non autorisé'; end if;
end $$;
revoke all on function public.set_character_gold(uuid,bigint) from public,anon;
grant execute on function public.set_character_gold(uuid,bigint) to authenticated;
