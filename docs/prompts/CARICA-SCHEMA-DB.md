psql -U sostitutoincloud -d sostitutoincloud -h localhost \
-f webapps/sostitutoincloud/docs/db/schema-target.sql


-- Poi verifica che le tabelle siano state create:
psql -U sostitutoincloud -d sostitutoincloud -h localhost -c "\dt"

-- Se Tutte le tabelle create correttamente! 🎉 Ora carica il seed:
psql -U sostitutoincloud -d sostitutoincloud -h localhost \
-f webapps/sostitutoincloud/docs/db/seed-data.sql