-- Elenco distinto dei proprietari con almeno una ritenuta nell'anno di competenza indicato.
-- NB: withholding_ledger non ha payment_date; si usa periodo_anno (anno di competenza), indicizzato.
SELECT DISTINCT wl.fk_owner_id
FROM withholding_ledger wl
WHERE wl.fk_tenant_id = ?
  AND wl.periodo_anno = ?
