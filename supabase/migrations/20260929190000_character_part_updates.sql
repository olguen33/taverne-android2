-- Update one editable field without rewriting a stale character sheet.
create function public.update_character_part(p_id uuid,p_field text,p_value text)
returns void language plpgsql security invoker set search_path=public,pg_temp as $$
begin
  if p_field='inventory' then
    update public.characters set inventory=coalesce(p_value,'')
      where id=p_id and owner_id=(select auth.uid());
  elsif p_field='campaign_notes' then
    update public.characters set campaign_notes=coalesce(p_value,'')
      where id=p_id and owner_id=(select auth.uid());
  else
    raise exception 'Champ non modifiable';
  end if;
  if not found then raise exception 'Personnage non autorisé'; end if;
end $$;
revoke all on function public.update_character_part(uuid,text,text) from public,anon;
grant execute on function public.update_character_part(uuid,text,text) to authenticated;
