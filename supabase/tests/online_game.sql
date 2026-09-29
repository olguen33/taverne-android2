set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
select public.set_pseudo('maitre_jeu');
select public.save_character(null,'Éclaireur','Elfe','Archer','Vie sauvage','','','','');
select public.set_character_gold((select id from public.characters where name='Éclaireur'),300);
select public.publish_contract('Deuxième quête','Une aventure à deux',401,2,4,.46,.36,array[0,2],1200,1380);
do $$ begin
  if (select count(*) from public.slots s join public.contracts c on c.id=s.contract_id where c.title='Deuxième quête')<>2 then raise exception 'Créneaux absents'; end if;
end $$;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
do $$ begin
  if (select count(*) from public.shared_character_names() where name='Éclaireur')<>1 then raise exception 'Nom du partenaire invisible'; end if;
  if (select count(*) from public.characters where name='Éclaireur')<>0 then raise exception 'Fiche privée exposée'; end if;
end $$;
insert into public.rumours(id,owner_id,description,map_x,map_y) values
  ('00000000-0000-0000-0000-000000000099',auth.uid(),'Une piste près du fort',.46,.36);
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
do $$ begin
  if (select count(*) from public.rumours where description='Une piste près du fort')<>1 then raise exception 'Rumeur invisible'; end if;
end $$;
