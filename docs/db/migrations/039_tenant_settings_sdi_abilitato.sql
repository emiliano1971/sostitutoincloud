-- ============================================
-- Migration 039: invio SDI disabilitabile per tenant
-- ============================================
--
-- Con sdi_abilitato = false l'applicazione non trasmette allo SDI: invio singolo, riprova,
-- invio massivo, auto-invio dopo l'emissione (fattura e NDC) ed elabora risposte sono
-- bloccati (SdiXmlService.verificaSdiAbilitato / isAutoInvioAttivo); sdi_auto_send viene
-- ignorato. Restano disponibili download XML, ZIP, "Inviata manualmente" e il suo annullo.
--
-- Default FALSE: anche le righe esistenti partono con l'invio disabilitato; il tenant lo
-- abilita da Impostazioni > Policy documentali.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/039_tenant_settings_sdi_abilitato.sql

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS sdi_abilitato BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN tenant_settings.sdi_abilitato IS
    'Se false la trasmissione allo SDI dall applicazione e disabilitata '
    '(invio singolo, riprova, massivo, auto-invio, elabora risposte); sdi_auto_send viene ignorato.';
