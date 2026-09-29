-- La Taverne: shared, authenticated game state. Apply to a new Supabase project.
create extension if not exists pgcrypto;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  pseudo text not null unique check (length(pseudo) between 3 and 32),
  imported_at timestamptz
);
create unique index profiles_pseudo_nocase on public.profiles(lower(pseudo));

create function public.create_profile() returns trigger language plpgsql security definer
set search_path = public, pg_temp as $$
begin
  insert into public.profiles(id, pseudo) values (new.id, 'joueur_' || right(replace(new.id::text,'-',''), 24));
  return new;
end $$;
create trigger profile_on_signup after insert on auth.users
for each row execute function public.create_profile();
revoke all on function public.create_profile() from public, anon, authenticated;

create table public.characters (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  legacy_id bigint,
  name text not null check (length(name) between 1 and 100),
  race text not null default '', archetype text not null default '', origin text not null default '',
  sheet text not null default '', lore text not null default '',
  inventory text not null default '', campaign_notes text not null default '',
  gold bigint not null default 0 check (gold between 0 and 1000000000),
  unique(owner_id, legacy_id)
);
create table public.contracts (
  id uuid primary key default gen_random_uuid(),
  proposer_id uuid not null references public.profiles(id),
  title text not null, description text not null,
  reward_text text not null default '', reward_gold bigint not null check (reward_gold between 1 and 1000000000),
  danger integer not null default 1 check (danger between 1 and 5),
  places integer not null default 4 check (places between 1 and 12),
  status text not null default 'ouvert' check (status in ('ouvert','planifié','terminé')),
  paid_out boolean not null default false,
  locked_slot_id uuid, locked_date date,
  map_x real, map_y real
);
create table public.slots (
  id uuid primary key default gen_random_uuid(),
  contract_id uuid not null references public.contracts(id) on delete cascade,
  weekday integer not null check (weekday between 0 and 6),
  start_minute integer not null check (start_minute between 0 and 1439),
  end_minute integer not null check (end_minute between 0 and 1439),
  check (start_minute <> end_minute),
  unique(contract_id, weekday, start_minute, end_minute),
  unique(id, contract_id)
);
alter table public.contracts add constraint locked_slot_belongs_to_contract
  foreign key (locked_slot_id, id) references public.slots(id, contract_id);
create table public.participants (
  contract_id uuid not null references public.contracts(id) on delete cascade,
  character_id uuid not null references public.characters(id) on delete cascade,
  primary key(contract_id, character_id)
);
create table public.votes (
  slot_id uuid not null references public.slots(id) on delete cascade,
  character_id uuid not null references public.characters(id) on delete cascade,
  primary key(slot_id, character_id)
);
create table public.messages (
  id bigint generated always as identity primary key,
  contract_id uuid not null references public.contracts(id) on delete cascade,
  character_id uuid not null references public.characters(id) on delete cascade,
  body text not null check (length(body) between 1 and 4000),
  created_at timestamptz not null default now()
);
create table public.catalog (
  id text primary key, name text not null, description text not null,
  price integer not null check (price > 0), active boolean not null default true
);
insert into public.catalog(id,name,description,price) values
('small_heal','Petite potion de soin','Rend 1D6 PV',200),
('large_heal','Grande potion de soin','Rend 2D6 PV',450),
('antidote','Antidote','Neutralise un poison ordinaire (accord du MJ)',150),
('focus','Potion de concentration','Aide à se concentrer (accord du MJ)',220),
('bandage','Bandage','Permet de panser une blessure',60),
('smoke','Fumigène','Crée un écran de fumée bref',120),
('oil','Fiole d’huile','Alimente une lampe ou peut être versée',40),
('snare','Collet','Immobilise une petite proie, usage unique',90),
('spikes','Piège à pointes','1D10 dégâts, pénétration 1, usage unique',180),
('jaws','Piège à mâchoires','1D10 dégâts, pénétration 2, usage unique',250);

alter table public.profiles enable row level security;
alter table public.characters enable row level security;
alter table public.contracts enable row level security;
alter table public.slots enable row level security;
alter table public.participants enable row level security;
alter table public.votes enable row level security;
alter table public.messages enable row level security;
alter table public.catalog enable row level security;

revoke all on public.profiles, public.characters, public.contracts, public.slots,
  public.participants, public.votes, public.messages, public.catalog from public, anon, authenticated;
grant select on public.profiles, public.contracts, public.slots, public.participants,
  public.votes, public.messages, public.catalog to authenticated;
grant select, insert, delete on public.characters to authenticated;
grant update (pseudo) on public.profiles to authenticated;
grant update (name,race,archetype,origin,sheet,lore,inventory,campaign_notes) on public.characters to authenticated;
grant insert (id,proposer_id,title,description,reward_text,reward_gold,danger,places,map_x,map_y) on public.contracts to authenticated;
grant update (title,description,reward_text,reward_gold,danger,places,map_x,map_y) on public.contracts to authenticated;
grant delete on public.contracts to authenticated;
grant insert (id,contract_id,weekday,start_minute,end_minute), delete on public.slots to authenticated;
grant insert (contract_id,character_id,body) on public.messages to authenticated;
grant usage on sequence public.messages_id_seq to authenticated;

create policy profiles_read on public.profiles for select to authenticated using (true);
create policy profiles_update on public.profiles for update to authenticated using (id=auth.uid()) with check (id=auth.uid());
create policy characters_read on public.characters for select to authenticated using (owner_id=auth.uid());
create policy characters_insert on public.characters for insert to authenticated with check (owner_id=auth.uid() and gold=0 and legacy_id is null);
create policy characters_update on public.characters for update to authenticated using (owner_id=auth.uid()) with check (owner_id=auth.uid());
create policy characters_delete on public.characters for delete to authenticated using (owner_id=auth.uid());
create policy contracts_read on public.contracts for select to authenticated using (true);
create policy contracts_insert on public.contracts for insert to authenticated with check (proposer_id=auth.uid() and status='ouvert' and not paid_out and locked_slot_id is null and locked_date is null);
create policy contracts_update on public.contracts for update to authenticated using (proposer_id=auth.uid() and status='ouvert' and not paid_out) with check (proposer_id=auth.uid());
create policy contracts_delete on public.contracts for delete to authenticated using (proposer_id=auth.uid() and not paid_out);
create policy slots_read on public.slots for select to authenticated using (true);
create policy slots_insert on public.slots for insert to authenticated with check (exists(select 1 from public.contracts c where c.id=contract_id and c.proposer_id=auth.uid() and c.status='ouvert'));
create policy slots_delete on public.slots for delete to authenticated using (exists(select 1 from public.contracts c where c.id=contract_id and c.proposer_id=auth.uid() and c.status='ouvert'));
create policy participants_read on public.participants for select to authenticated using (true);
create policy votes_read on public.votes for select to authenticated using (true);
create policy messages_read on public.messages for select to authenticated using (true);
create policy messages_insert on public.messages for insert to authenticated with check (
  exists(select 1 from public.characters c where c.id=character_id and c.owner_id=auth.uid())
  and exists(select 1 from public.participants p where p.contract_id=messages.contract_id and p.character_id=messages.character_id)
);
create policy catalog_read on public.catalog for select to authenticated using (true);

-- Only these functions may change balances, votes, schedule, or payout state.
create function public.buy_consumable(p_character uuid,p_item text) returns bigint
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_price integer; v_name text; v_balance bigint;
begin
  select price,name into v_price,v_name from public.catalog where id=p_item and active;
  if v_price is null then raise exception 'Objet indisponible'; end if;
  select gold into v_balance from public.characters where id=p_character and owner_id=auth.uid() for update;
  if v_balance is null or v_balance<v_price then raise exception 'Bourse insuffisante'; end if;
  update public.characters set gold=gold-v_price,
    inventory=inventory || case when inventory='' or right(inventory,1)=E'\n' then '' else E'\n' end || v_name || ' ×1'
    where id=p_character;
  return v_balance-v_price;
end $$;

create function public.join_and_vote(p_contract uuid,p_character uuid,p_slots uuid[]) returns void
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_contract public.contracts%rowtype; v_count integer;
begin
  select * into v_contract from public.contracts where id=p_contract for update;
  if v_contract.id is null or v_contract.status<>'ouvert' then raise exception 'Contrat fermé'; end if;
  if not exists(select 1 from public.characters where id=p_character and owner_id=auth.uid()) then raise exception 'Personnage non autorisé'; end if;
  select count(*) into v_count from public.participants where contract_id=p_contract;
  if v_count>=v_contract.places and not exists(select 1 from public.participants where contract_id=p_contract and character_id=p_character) then raise exception 'Groupe complet'; end if;
  if exists(select 1 from public.slots where contract_id=p_contract) and coalesce(array_length(p_slots,1),0)=0 then raise exception 'Choisis un créneau'; end if;
  if exists(select 1 from unnest(p_slots) s left join public.slots t on t.id=s and t.contract_id=p_contract where t.id is null) then raise exception 'Créneau inconnu'; end if;
  insert into public.participants(contract_id,character_id) values(p_contract,p_character) on conflict do nothing;
  delete from public.votes where character_id=p_character and slot_id in (select id from public.slots where contract_id=p_contract);
  insert into public.votes(slot_id,character_id) select distinct s,p_character from unnest(p_slots) s on conflict do nothing;
end $$;

create function public.lock_contract_date(p_contract uuid,p_slot uuid,p_date date) returns void
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_owner uuid; v_status text; v_locked uuid; v_date date; v_weekday integer;
begin
  select proposer_id,status,locked_slot_id,locked_date into v_owner,v_status,v_locked,v_date from public.contracts where id=p_contract for update;
  if v_owner is distinct from auth.uid() or v_status='terminé' or v_date is not null or (v_locked is not null and v_locked<>p_slot) then raise exception 'Date non modifiable'; end if;
  select weekday into v_weekday from public.slots where id=p_slot and contract_id=p_contract;
  if v_weekday is null or p_date is null or p_date<current_date or extract(isodow from p_date)::integer-1<>v_weekday then raise exception 'Date incompatible avec le créneau'; end if;
  update public.contracts set locked_slot_id=p_slot,locked_date=p_date,status='planifié' where id=p_contract;
end $$;

-- Called once by the owner after signing into both the old local account and the new online account.
-- Existing local file:// or content:// attachments remain on the phone until uploaded separately.
create function public.import_local_characters(p_pseudo text,p_characters jsonb) returns integer
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_item jsonb; v_count integer:=0; v_gold bigint; v_legacy bigint;
begin
  if auth.uid() is null then raise exception 'Connexion nécessaire'; end if;
  perform 1 from public.profiles where id=auth.uid() and imported_at is null for update;
  if not found then raise exception 'Compte local déjà importé'; end if;
  if p_pseudo is null or length(trim(p_pseudo)) not between 3 and 32 then raise exception 'Pseudo invalide'; end if;
  if jsonb_typeof(p_characters)<>'array' or jsonb_array_length(p_characters)>100 then raise exception 'Personnages invalides'; end if;
  for v_item in select value from jsonb_array_elements(p_characters) loop
    v_legacy:=(v_item->>'legacy_id')::bigint;
    v_gold:=(v_item->>'gold')::bigint;
    if v_legacy is null or v_gold not between 0 and 1000000000 or length(coalesce(v_item->>'name','')) not between 1 and 100 then raise exception 'Fiche locale invalide'; end if;
    insert into public.characters(owner_id,legacy_id,name,race,archetype,origin,sheet,lore,inventory,campaign_notes,gold)
    values(auth.uid(),v_legacy,v_item->>'name',coalesce(v_item->>'race',''),coalesce(v_item->>'archetype',''),
      coalesce(v_item->>'origin',''),coalesce(v_item->>'sheet',''),coalesce(v_item->>'lore',''),
      coalesce(v_item->>'inventory',''),coalesce(v_item->>'campaign_notes',''),v_gold);
    v_count:=v_count+1;
  end loop;
  update public.profiles set pseudo=trim(p_pseudo),imported_at=now() where id=auth.uid();
  return v_count;
end $$;

create function public.complete_contract(p_contract uuid) returns bigint
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_contract public.contracts%rowtype; v_count bigint; v_part bigint; v_rank bigint:=0; v_rec record;
begin
  select * into v_contract from public.contracts where id=p_contract for update;
  if v_contract.proposer_id is distinct from auth.uid() then raise exception 'MJ non autorisé'; end if;
  if v_contract.paid_out then return 0; end if;
  if v_contract.locked_date is null then raise exception 'Date à fixer avant la clôture'; end if;
  select count(*) into v_count from public.participants where contract_id=p_contract;
  if v_count=0 then raise exception 'Aucun participant'; end if;
  for v_rec in select c.id,c.gold from public.participants p join public.characters c on c.id=p.character_id
    where p.contract_id=p_contract order by c.id for update of c loop
    v_part:=v_contract.reward_gold/v_count+case when v_rank<v_contract.reward_gold%v_count then 1 else 0 end;
    if v_rec.gold+v_part>1000000000 then raise exception 'Bourse pleine'; end if;
    update public.characters set gold=gold+v_part where id=v_rec.id;
    v_rank:=v_rank+1;
  end loop;
  update public.contracts set status='terminé',paid_out=true where id=p_contract;
  return v_contract.reward_gold;
end $$;

revoke all on function public.buy_consumable(uuid,text),public.join_and_vote(uuid,uuid,uuid[]),
  public.lock_contract_date(uuid,uuid,date),public.complete_contract(uuid),
  public.import_local_characters(text,jsonb) from public,anon;
grant execute on function public.buy_consumable(uuid,text),public.join_and_vote(uuid,uuid,uuid[]),
  public.lock_contract_date(uuid,uuid,date),public.complete_contract(uuid),
  public.import_local_characters(text,jsonb) to authenticated;
