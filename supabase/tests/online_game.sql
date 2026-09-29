set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
select public.set_pseudo('maitre_jeu');
select public.save_character(null,'Éclaireur','Elfe','Archer','Vie sauvage','','','','');
select public.set_character_gold((select id from public.characters where name='Éclaireur'),300);
select public.publish_contract('Deuxième quête','Une aventure à deux',401,2,4,.46,.36,array[0,2],1200,1380);
select public.join_and_vote((select id from public.contracts where title='Deuxième quête'),
  (select id from public.characters where name='Éclaireur'),
  array[(select s.id from public.slots s join public.contracts c on c.id=s.contract_id where c.title='Deuxième quête' order by s.weekday limit 1)]);
do $$ begin
  if (select count(*) from public.slots s join public.contracts c on c.id=s.contract_id where c.title='Deuxième quête')<>2 then raise exception 'Créneaux absents'; end if;
end $$;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
do $$ begin
  if (select count(*) from public.shared_character_names() where name='Éclaireur')<>1 then raise exception 'Nom du partenaire invisible'; end if;
  if (select count(*) from public.characters where name='Éclaireur')<>0 then raise exception 'Fiche privée exposée'; end if;
  begin
    perform public.set_character_gold((select id from public.shared_character_names() where name='Éclaireur'),999999);
    raise exception 'Modification de la bourse d’autrui autorisée';
  exception when sqlstate 'P0001' then
    if sqlerrm='Modification de la bourse d’autrui autorisée' then raise; end if;
  end;
  begin
    perform public.complete_contract((select id from public.contracts where title='Deuxième quête'));
    raise exception 'Paiement par un autre joueur autorisé';
  exception when sqlstate 'P0001' then
    if sqlerrm='Paiement par un autre joueur autorisé' then raise; end if;
  end;
end $$;
insert into public.rumours(id,owner_id,description,map_x,map_y) values
  ('00000000-0000-0000-0000-000000000099',auth.uid(),'Une piste près du fort',.46,.36);
select public.edit_rumour('00000000-0000-0000-0000-000000000099','Une piste au nord',.46,.37);
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
do $$ begin
  if (select count(*) from public.rumours where description='Une piste au nord')<>1 then raise exception 'Rumeur invisible'; end if;
end $$;
