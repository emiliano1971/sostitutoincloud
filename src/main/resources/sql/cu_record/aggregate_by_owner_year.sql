-- Aggrega le ritenute operate per un proprietario in un anno fiscale (anno di competenza).
-- NB: lo schema reale di withholding_ledger non ha le colonne gross_due_to_beneficiary /
--     taxable_base / withholding_amount / payment_date. Mappatura sulle colonne effettive:
--       total_compensi   = SUM(canone_locazione)   (compenso lordo al proprietario = canone)
--       total_imponibile = SUM(canone_locazione)   (base imponibile = canone)
--       total_ritenute   = SUM(ritenuta_amount)
--     L'anno è periodo_anno (anno di competenza), indicizzato.
SELECT
    SUM(wl.canone_locazione) AS total_compensi,
    SUM(wl.canone_locazione) AS total_imponibile,
    SUM(wl.ritenuta_amount)  AS total_ritenute
FROM withholding_ledger wl
WHERE wl.fk_tenant_id = ?
  AND wl.fk_owner_id  = ?
  AND wl.periodo_anno = ?
