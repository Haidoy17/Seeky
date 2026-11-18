-- Seeky cloud snapshots v1. Apply as project owner in Supabase SQL editor.
-- OAuth provider secrets belong in Supabase Auth settings, never in the Android APK.
begin;
create schema if not exists seeky_private;
revoke all on schema seeky_private from public,anon;
grant usage on schema seeky_private to authenticated;
create table if not exists public.seeky_heads (
 user_id uuid primary key references auth.users(id) on delete cascade,
 revision bigint not null default 0 check(revision>=0),
 backup_id uuid,
 updated_at timestamptz not null default now()
);
create table if not exists public.seeky_backups (
 id uuid primary key,
 user_id uuid not null references auth.users(id) on delete cascade,
 base_revision bigint not null check(base_revision>=0),
 revision bigint,
 bytes bigint not null check(bytes>0 and bytes<=536870912),
 sha256 text not null check(sha256 ~ '^[a-f0-9]{64}$'),
 chunks jsonb not null check(jsonb_typeof(chunks)='array' and jsonb_array_length(chunks) between 1 and 64),
 device text not null check(length(device) between 1 and 120),
 committed boolean not null default false,
 created_at timestamptz not null default now(),
 committed_at timestamptz
);
create index if not exists seeky_backups_owner on public.seeky_backups(user_id,created_at desc);
alter table public.seeky_heads enable row level security;
alter table public.seeky_backups enable row level security;
revoke all on public.seeky_heads,public.seeky_backups from anon,authenticated;
grant select on public.seeky_heads,public.seeky_backups to authenticated;
drop policy if exists seeky_own_head on public.seeky_heads;
create policy seeky_own_head on public.seeky_heads for select to authenticated using(user_id=(select auth.uid()));
drop policy if exists seeky_own_backups on public.seeky_backups;
create policy seeky_own_backups on public.seeky_backups for select to authenticated using(user_id=(select auth.uid()));

create or replace function seeky_private.begin_backup(p_id uuid,p_base bigint,p_bytes bigint,p_sha text,p_chunks jsonb,p_device text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare u uuid:=auth.uid(); current_head public.seeky_heads; item jsonb; size_sum bigint:=0; n integer:=0;
begin
 if u is null then raise exception 'Authentication required' using errcode='42501'; end if;
 if p_base<0 or p_bytes<=0 or p_bytes>536870912 or p_sha !~ '^[a-f0-9]{64}$' or length(p_device) not between 1 and 120 or jsonb_typeof(p_chunks)<>'array' or jsonb_array_length(p_chunks) not between 1 and 64 then raise exception 'Invalid snapshot'; end if;
 for item in select * from jsonb_array_elements(p_chunks) loop
  if item->>'sha256' is null or item->>'sha256' !~ '^[a-f0-9]{64}$' or (item->>'bytes')::bigint not between 1 and 8388608 then raise exception 'Invalid chunk'; end if;
  size_sum:=size_sum+(item->>'bytes')::bigint;n:=n+1;
 end loop;
 if size_sum<>p_bytes then raise exception 'Chunk total mismatch'; end if;
 insert into public.seeky_heads(user_id) values(u) on conflict do nothing;
 select * into current_head from public.seeky_heads where user_id=u for update;
 if current_head.revision<>p_base then raise exception 'Cloud changed; restore or review first' using errcode='40001'; end if;
 if exists(select 1 from public.seeky_backups where id=p_id) then
  if not exists(select 1 from public.seeky_backups where id=p_id and user_id=u and sha256=p_sha and bytes=p_bytes and chunks=p_chunks and base_revision=p_base) then raise exception 'Snapshot identity mismatch' using errcode='42501'; end if;
 else
  insert into public.seeky_backups(id,user_id,base_revision,bytes,sha256,chunks,device) values(p_id,u,p_base,p_bytes,p_sha,p_chunks,p_device);
 end if;
 return jsonb_build_object('id',p_id,'base_revision',p_base);
end $$;

create or replace function seeky_private.commit_backup(p_id uuid,p_base bigint)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare u uuid:=auth.uid(); head public.seeky_heads; backup public.seeky_backups; i integer; object_path text;
begin
 if u is null then raise exception 'Authentication required' using errcode='42501'; end if;
 select * into head from public.seeky_heads where user_id=u for update;
 select * into backup from public.seeky_backups where id=p_id and user_id=u for update;
 if not found then raise exception 'Unknown snapshot' using errcode='42501'; end if;
 if head.backup_id=p_id and backup.committed then return jsonb_build_object('id',p_id,'revision',head.revision); end if;
 if head.revision<>p_base or backup.base_revision<>p_base or backup.committed then raise exception 'Cloud changed; restore or review first' using errcode='40001'; end if;
 for i in 0..jsonb_array_length(backup.chunks)-1 loop
  object_path:=u::text||'/'||p_id::text||'/'||lpad(i::text,5,'0')||'.bin';
  if not exists(select 1 from storage.objects where bucket_id='seeky-private' and name=object_path and (metadata->>'size')::bigint=(backup.chunks->i->>'bytes')::bigint) then raise exception 'Incomplete snapshot'; end if;
 end loop;
 update public.seeky_backups set committed=true,revision=p_base+1,committed_at=now() where id=p_id;
 update public.seeky_heads set backup_id=p_id,revision=p_base+1,updated_at=now() where user_id=u;
 return jsonb_build_object('id',p_id,'revision',p_base+1);
end $$;
revoke all on function seeky_private.begin_backup(uuid,bigint,bigint,text,jsonb,text) from public,anon;
revoke all on function seeky_private.commit_backup(uuid,bigint) from public,anon;
grant execute on function seeky_private.begin_backup(uuid,bigint,bigint,text,jsonb,text) to authenticated;
grant execute on function seeky_private.commit_backup(uuid,bigint) to authenticated;

-- Configure this private bucket in Storage first or let this supported bucket insert create it.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('seeky-private','seeky-private',false,8388608,array['application/octet-stream'])
on conflict(id) do update set public=false,file_size_limit=8388608,allowed_mime_types=array['application/octet-stream'];
drop policy if exists seeky_objects_read on storage.objects;
create policy seeky_objects_read on storage.objects for select to authenticated using(bucket_id='seeky-private' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists seeky_objects_insert on storage.objects;
create policy seeky_objects_insert on storage.objects for insert to authenticated with check(
 bucket_id='seeky-private' and (storage.foldername(name))[1]=(select auth.uid())::text and
 exists(select 1 from public.seeky_backups b where b.user_id=(select auth.uid()) and b.id::text=(storage.foldername(name))[2] and not b.committed and
 name ~ ('^'||auth.uid()::text||'/'||b.id::text||'/[0-9]{5}\.bin$') and
 substring(split_part(name,'/',3),1,5)::integer<jsonb_array_length(b.chunks))
);
-- Keep the latest three completed versions; older or unfinished copies may be removed by their owner.
create or replace function seeky_private.can_remove_backup(p_id text)
returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select auth.uid() is not null and exists(
  select 1 from public.seeky_backups b where b.id::text=p_id and b.user_id=auth.uid()
  and not exists(select 1 from public.seeky_heads h where h.user_id=auth.uid() and h.backup_id=b.id)
  and (not b.committed or b.id not in(select id from public.seeky_backups where user_id=auth.uid() and committed order by revision desc limit 3))
 )
$$;
revoke all on function seeky_private.can_remove_backup(text) from public,anon;
grant execute on function seeky_private.can_remove_backup(text) to authenticated;
drop policy if exists seeky_objects_delete on storage.objects;
create policy seeky_objects_delete on storage.objects for delete to authenticated using(
 bucket_id='seeky-private' and (storage.foldername(name))[1]=(select auth.uid())::text and seeky_private.can_remove_backup((storage.foldername(name))[2])
);
create or replace function seeky_private.prune_backup(p_id uuid)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not seeky_private.can_remove_backup(p_id::text) then raise exception 'Protected snapshot' using errcode='42501'; end if;
 if exists(select 1 from storage.objects where bucket_id='seeky-private' and name like auth.uid()::text||'/'||p_id::text||'/%') then raise exception 'Delete objects through Storage API first'; end if;
 delete from public.seeky_backups where id=p_id and user_id=auth.uid();
end $$;
revoke all on function seeky_private.prune_backup(uuid) from public,anon;
grant execute on function seeky_private.prune_backup(uuid) to authenticated;

-- Public Data API contains invoker-only wrappers; privileged implementation stays unexposed.
create or replace function public.seeky_begin_backup(p_id uuid,p_base bigint,p_bytes bigint,p_sha text,p_chunks jsonb,p_device text)
returns jsonb language sql security invoker set search_path='' as $$ select seeky_private.begin_backup(p_id,p_base,p_bytes,p_sha,p_chunks,p_device) $$;
create or replace function public.seeky_commit_backup(p_id uuid,p_base bigint)
returns jsonb language sql security invoker set search_path='' as $$ select seeky_private.commit_backup(p_id,p_base) $$;
create or replace function public.seeky_can_remove_backup(p_id text)
returns boolean language sql stable security invoker set search_path='' as $$ select seeky_private.can_remove_backup(p_id) $$;
create or replace function public.seeky_prune_backup(p_id uuid)
returns void language sql security invoker set search_path='' as $$ select seeky_private.prune_backup(p_id) $$;
revoke all on function public.seeky_begin_backup(uuid,bigint,bigint,text,jsonb,text) from public,anon;
revoke all on function public.seeky_commit_backup(uuid,bigint) from public,anon;
revoke all on function public.seeky_can_remove_backup(text) from public,anon;
revoke all on function public.seeky_prune_backup(uuid) from public,anon;
grant execute on function public.seeky_begin_backup(uuid,bigint,bigint,text,jsonb,text) to authenticated;
grant execute on function public.seeky_commit_backup(uuid,bigint) to authenticated;
grant execute on function public.seeky_can_remove_backup(text) to authenticated;
grant execute on function public.seeky_prune_backup(uuid) to authenticated;

commit;
