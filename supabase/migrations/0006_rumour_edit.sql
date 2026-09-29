create function public.edit_rumour(p_id uuid,p_description text,p_x real,p_y real) returns void
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if length(trim(coalesce(p_description,''))) not between 1 and 2000 or p_x not between 0 and 1 or p_y not between 0 and 1 then raise exception 'Rumeur invalide'; end if;
 update public.rumours set description=trim(p_description),map_x=p_x,map_y=p_y where id=p_id and owner_id=auth.uid();
 if not found then raise exception 'Rumeur non autorisée'; end if;
end $$;
revoke all on function public.edit_rumour(uuid,text,real,real) from public,anon;
grant execute on function public.edit_rumour(uuid,text,real,real) to authenticated;
