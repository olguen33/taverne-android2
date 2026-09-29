create index contracts_proposer_idx on public.contracts(proposer_id);
create index contracts_locked_slot_idx on public.contracts(locked_slot_id,id) where locked_slot_id is not null;
create index messages_contract_order_idx on public.messages(contract_id,id);
create index messages_character_idx on public.messages(character_id);
create index participants_character_idx on public.participants(character_id);
create index votes_character_idx on public.votes(character_id);
create index rumours_owner_idx on public.rumours(owner_id);
