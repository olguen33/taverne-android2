insert into auth.users(id) values
  ('00000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000002');

set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
update public.profiles set pseudo='mj_test' where id=auth.uid();
insert into public.characters(id,owner_id,name) values
  ('00000000-0000-0000-0000-000000000011',auth.uid(),'Guerrier');
insert into public.contracts(id,proposer_id,title,description,reward_text,reward_gold) values
  ('00000000-0000-0000-0000-000000000021',auth.uid(),'Essai','Contrat de test','401 po',401);
insert into public.slots(id,contract_id,weekday,start_minute,end_minute) values
  ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000021',0,1200,1380);
select public.join_and_vote('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000011',array['00000000-0000-0000-0000-000000000031']::uuid[]);

set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
update public.profiles set pseudo='joueur_test' where id=auth.uid();
insert into public.characters(id,owner_id,name) values
  ('00000000-0000-0000-0000-000000000012',auth.uid(),'Mage');
select public.join_and_vote('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000012',array['00000000-0000-0000-0000-000000000031']::uuid[]);
do $$ begin
  if (select count(*) from public.characters)<>1 then raise exception 'RLS: personnage d’autrui visible'; end if;
end $$;

set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
select public.lock_contract_date('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000031',
  current_date+((8-extract(isodow from current_date)::integer)%7));
do $$ begin
  if public.complete_contract('00000000-0000-0000-0000-000000000021')<>401 then raise exception 'Premier versement absent'; end if;
  if public.complete_contract('00000000-0000-0000-0000-000000000021')<>0 then raise exception 'Double versement'; end if;
  if (select gold from public.characters where owner_id=auth.uid())<>201 then raise exception 'Part MJ incorrecte'; end if;
end $$;
select public.buy_consumable('00000000-0000-0000-0000-000000000011','small_heal');
do $$ begin
  if (select gold from public.characters where owner_id=auth.uid())<>1 then raise exception 'Achat non débité'; end if;
  if (select inventory from public.characters where owner_id=auth.uid()) not like '%Petite potion de soin%' then raise exception 'Achat absent de l’inventaire'; end if;
end $$;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
do $$ begin
  if (select gold from public.characters where owner_id=auth.uid())<>200 then raise exception 'Part joueur incorrecte'; end if;
end $$;
