\echo '=== Sostituto in Cloud — DB Init ==='
\echo 'Step 1: schema...'
\i schema-dump.sql
\echo 'Step 2: dati iniziali...'
\i seed-dump.sql
\echo '=== Installazione completata ==='
