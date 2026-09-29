-- Run against the linked project as an administrator. All test data is rolled back.
begin;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-00000000a101','{"pseudo":"Essai A"}'::jsonb),
 ('00000000-0000-0000-0000-00000000b202','{"pseudo":"Essai B"}'::jsonb);
set local role authenticated;
do $$
declare a uuid:='00000000-0000-0000-0000-00000000a101';
 b uuid:='00000000-0000-0000-0000-00000000b202';
 ch uuid; ct uuid; monday uuid; wednesday uuid;
begin
 perform set_config('request.jwt.claim.sub',a::text,true);
 perform public.set_pseudo('Essai A');
 ct:=public.publish_contract('Essai multijoueur','Contrat temporaire',401,2,4,.46,.36,array[0,2],1260,60);
 select id into monday from public.slots where contract_id=ct and weekday=0;
 select id into wednesday from public.slots where contract_id=ct and weekday=2;
 perform set_config('request.jwt.claim.sub',b::text,true);
 perform public.set_pseudo('Essai B');
 ch:=public.save_character(null,'Éclaireur essai','Elfe','Archer','Vie sauvage','','','','');
 perform public.join_and_vote(ct,ch,array[monday]);
 perform public.join_and_vote(ct,ch,array[monday]);
 if (select count(*) from public.votes where character_id=ch)<>1 then raise exception 'Vote en double'; end if;
 perform public.join_and_vote(ct,ch,array[wednesday]);
 if (select count(*) from public.votes where character_id=ch and slot_id=wednesday)<>1
    or (select count(*) from public.votes where character_id=ch)<>1 then raise exception 'Modification du vote incorrecte'; end if;
 insert into public.campaign_entries(author_id,title,body) values(b,'Note essai','Visible pour tous');
 perform set_config('request.jwt.claim.sub',a::text,true);
 if (select count(*) from public.contracts where id=ct and status='ouvert')<>1 then raise exception 'Contrat absent chez A'; end if;
 if (select count(*) from public.votes v join public.shared_character_names() n on n.id=v.character_id
      join public.profiles p on p.id=n.owner_id where v.slot_id=wednesday and p.pseudo='Essai B')<>1
   then raise exception 'Pseudo votant absent'; end if;
 if (select count(*) from public.characters where id=ch)<>0 then raise exception 'Fiche privée exposée'; end if;
 if (select count(*) from public.campaign_entries where title='Note essai')<>1 then raise exception 'Campagne non partagée'; end if;
 perform public.edit_contract(ct,'Essai modifié','Description modifiée',401,2,4,.46,.36,array[0,2],1260,60);
 perform public.lock_contract_date(ct,monday,(current_date + ((8-extract(isodow from current_date)::int)%7)));
 perform public.complete_contract(ct);
 if (select count(*) from public.contracts where id=ct and status='terminé')<>1 then raise exception 'Archivage incorrect'; end if;
 perform set_config('request.jwt.claim.sub',b::text,true);
 if (select count(*) from public.contracts where id=ct and title='Essai modifié' and status='terminé')<>1
   then raise exception 'Modification ou archive invisible chez B'; end if;
end $$;
rollback;
