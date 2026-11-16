-- CU orfane: cu_record del tenant per l'anno senza alcuna ritenuta valida nel registro
-- (stesso filtro di aggregate_by_owner_year.sql: fk_ndc_id IS NULL). Tutti gli stati:
-- il service elimina solo draft/generated e segnala sent/delivered.
SELECT cu.id, cu.fk_tenant_id, cu.fk_owner_id, cu.tax_year, cu.total_compensi, cu.total_ritenute,
       cu.total_imponibile, cu.stato, cu.generated_at, cu.sent_at, cu.created_at, cu.updated_at
FROM cu_record cu
WHERE cu.fk_tenant_id = ?
  AND cu.tax_year = ?
  AND NOT EXISTS (
      SELECT 1
      FROM withholding_ledger wl
      WHERE wl.fk_tenant_id = cu.fk_tenant_id
        AND wl.fk_owner_id  = cu.fk_owner_id
        AND wl.periodo_anno = cu.tax_year
        -- migration 026: esclude ritenute stornate e crediti da nota di credito
        AND wl.fk_ndc_id IS NULL
  )
ORDER BY cu.id
