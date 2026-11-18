-- Transaction-only authorization checks. No accounts, study rows or blobs persist.
begin;
create temporary table seeky_verification(a uuid,b uuid,snapshot uuid) on commit drop;
insert into seeky_verification select gen_random_uuid(),gen_random_uuid(),gen_random_uuid();
insert into auth.users(id) select a from seeky_verification union all select b from seeky_verification;
grant select on seeky_verification to authenticated,anon;
create function pg_temp.seeky_assert(ok boolean,label text) returns void language plpgsql as $$ begin if ok is distinct from true then raise exception 'Verification failed: %',label;end if;end $$;
set local role authenticated;
select set_config('request.jwt.claim.sub',(select a::text from seeky_verification),true);
select public.seeky_begin_backup((select snapshot from seeky_verification),0,2,repeat('a',64),jsonb_build_array(jsonb_build_object('bytes',2,'sha256',repeat('a',64))),'transaction-only verification');
select pg_temp.seeky_assert((select count(*)=1 from public.seeky_backups),'owner sees own pending snapshot');
do $$ begin
 begin
  perform public.seeky_commit_backup((select snapshot from seeky_verification),0);
  raise exception 'Verification failed: incomplete snapshot committed';
 exception when others then if sqlerrm not like '%Incomplete snapshot%' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub',(select b::text from seeky_verification),true);
select pg_temp.seeky_assert((select count(*)=0 from public.seeky_backups),'second account cannot see first snapshot');
select pg_temp.seeky_assert((select count(*)=0 from public.seeky_heads),'second account cannot see first head');
do $$ begin
 begin
  perform public.seeky_commit_backup((select snapshot from seeky_verification),0);
  raise exception 'Verification failed: another owner could commit';
 exception when insufficient_privilege then null;end;
end $$;
reset role;
set local role anon;
select set_config('request.jwt.claim.sub','',true);
do $$ begin
 begin
  perform public.seeky_begin_backup((select snapshot from seeky_verification),0,2,repeat('a',64),jsonb_build_array(jsonb_build_object('bytes',2,'sha256',repeat('a',64))),'verification');
  raise exception 'Verification failed: anonymous publication allowed';
 exception when insufficient_privilege then null;end;
end $$;
reset role;
rollback;
select jsonb_build_object('authorization_checks_passed',6,'test_rows_rolled_back',true,'bucket_private',(select not public from storage.buckets where id='seeky-private'),'storage_chunk_limit',(select file_size_limit from storage.buckets where id='seeky-private'),'persistent_backups',(select count(*) from public.seeky_backups)) as verification;
