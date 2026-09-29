create or replace function public.shared_character_names()
returns table(id uuid, name text, owner_id uuid)
language sql stable security definer set search_path=public,pg_temp as $$
  select c.id,c.name,c.owner_id from public.characters c
  where c.owner_id=auth.uid() or exists(select 1 from public.participants p where p.character_id=c.id)
$$;
