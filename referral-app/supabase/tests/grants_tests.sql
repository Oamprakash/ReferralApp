-- Every table in public has explicit grants (Supabase no longer adds them automatically).
-- Expect: no rows in G1 and G2; G3 lists the authenticated privileges on purpose.
\pset footer off
\echo '--- G1 tables service_role cannot fully use (expect none)'
select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
  and not (has_table_privilege('service_role', c.oid, 'select') and has_table_privilege('service_role', c.oid, 'insert')
       and has_table_privilege('service_role', c.oid, 'update') and has_table_privilege('service_role', c.oid, 'delete'))
order by 1;
\echo '--- G2 tables anon can touch (expect none)'
select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
  and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('anon', c.oid, 'insert')
    or has_table_privilege('anon', c.oid, 'update') or has_table_privilege('anon', c.oid, 'delete'))
order by 1;
\echo '--- G3 what signed-in users can do directly (RLS still limits rows)'
select c.relname as tbl,
  concat_ws(',',
    case when has_table_privilege('authenticated', c.oid, 'select') then 'select' end,
    case when has_table_privilege('authenticated', c.oid, 'insert') then 'insert' end,
    case when has_table_privilege('authenticated', c.oid, 'update') then 'update'
         when has_any_column_privilege('authenticated', c.oid, 'update') then 'update(some cols)' end,
    case when has_table_privilege('authenticated', c.oid, 'delete') then 'delete' end) as authenticated
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r' order by 1;
