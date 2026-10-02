package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.WithholdingLedgerRowMapper;
import it.gavia.sostitutoincloud.dto.fiscal.WithholdingLedgerDTO;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j2
@Repository
public class WithholdingLedgerDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, fk_booking_id, fk_fiscal_document_id, " +
            "periodo_mese, periodo_anno, canone_locazione, aliquota_ritenuta, ritenuta_amount, " +
            "data_evento, stato, fk_f24_record_id, fk_ndc_id, fk_ledger_origine_id, created_at, updated_at FROM withholding_ledger";

    /**
     * Esclude le righe toccate da una nota di credito (migration 026): ritenute stornate,
     * righe di credito d'imposta e ritenute già versate di un booking stornato. Non devono
     * entrare né nelle liquidazioni né nella CU.
     */
    private static final String SENZA_NDC = " AND fk_ndc_id IS NULL";
    private static final String SENZA_NDC_WL = " AND wl.fk_ndc_id IS NULL";

    private final JdbcTemplate jdbcTemplate;
    private final WithholdingLedgerRowMapper rowMapper = new WithholdingLedgerRowMapper();

    public WithholdingLedgerDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<WithholdingLedger> findById(Integer id) {
        log.debug("WithholdingLedgerDAO.findById() - id={}", id);
        List<WithholdingLedger> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", rowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<WithholdingLedger> findByTenantAndPeriodo(Integer tenantId, Integer anno, Integer mese) {
        log.debug("WithholdingLedgerDAO.findByTenantAndPeriodo() - tenantId={}, anno={}, mese={}", tenantId, anno, mese);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND periodo_anno = ? AND periodo_mese = ? ORDER BY data_evento, id";
        return jdbcTemplate.query(sql, rowMapper, tenantId, anno, mese);
    }

    public List<WithholdingLedger> findByOwner(Integer tenantId, Integer ownerId, Integer anno) {
        log.debug("WithholdingLedgerDAO.findByOwner() - tenantId={}, ownerId={}, anno={}", tenantId, ownerId, anno);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? AND periodo_anno = ? ORDER BY periodo_mese, id";
        return jdbcTemplate.query(sql, rowMapper, tenantId, ownerId, anno);
    }

    /** Una sola ritenuta per documento fiscale (vincolo uq_withholding_per_document). */
    public Optional<WithholdingLedger> findByFiscalDocumentId(Integer fiscalDocumentId) {
        log.debug("WithholdingLedgerDAO.findByFiscalDocumentId() - fiscalDocumentId={}", fiscalDocumentId);
        List<WithholdingLedger> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE fk_fiscal_document_id = ?", rowMapper, fiscalDocumentId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<WithholdingLedger> findByBookingId(Integer bookingId) {
        log.debug("WithholdingLedgerDAO.findByBookingId() - bookingId={}", bookingId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_booking_id = ? ORDER BY id", rowMapper, bookingId);
    }

    public void deleteByBookingId(Integer bookingId) {
        log.debug("WithholdingLedgerDAO.deleteByBookingId() - bookingId={}", bookingId);
        jdbcTemplate.update("DELETE FROM withholding_ledger WHERE fk_booking_id = ?", bookingId);
    }

    public List<WithholdingLedger> findByF24Record(Integer f24RecordId) {
        log.debug("WithholdingLedgerDAO.findByF24Record() - f24RecordId={}", f24RecordId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_f24_record_id = ? ORDER BY id", rowMapper, f24RecordId);
    }

    /**
     * Righe di ritenuta di un F24 con i dati di prenotazione e immobile già risolti.
     * Query esternalizzata in sql/withholding_ledger/righe_f24.sql.
     */
    public List<WithholdingLedgerDTO> findRigheF24(Integer f24RecordId, Integer tenantId) {
        String sql = loadSql("sql/withholding_ledger/righe_f24.sql");
        List<WithholdingLedgerDTO> result = jdbcTemplate.query(sql, DETTAGLIO_MAPPER, f24RecordId, tenantId);
        log.debug("WithholdingLedgerDAO.findRigheF24() - f24RecordId={} tenantId={} righe={}",
                f24RecordId, tenantId, result.size());
        return result;
    }

    /**
     * Righe di ritenuta di un periodo con gli stessi dati risolti di {@link #findRigheF24}.
     * Query esternalizzata in sql/withholding_ledger/righe_periodo.sql.
     */
    public List<WithholdingLedgerDTO> findRighePeriodo(Integer tenantId, Integer anno, Integer mese) {
        String sql = loadSql("sql/withholding_ledger/righe_periodo.sql");
        List<WithholdingLedgerDTO> result = jdbcTemplate.query(sql, DETTAGLIO_MAPPER, tenantId, anno, mese);
        log.debug("WithholdingLedgerDAO.findRighePeriodo() - tenantId={} periodo={}/{} righe={}",
                tenantId, mese, anno, result.size());
        return result;
    }

    /** Proiezione condivisa dalle due query di dettaglio (righe_f24.sql, righe_periodo.sql). */
    private static final RowMapper<WithholdingLedgerDTO> DETTAGLIO_MAPPER = (rs, rowNum) ->
            WithholdingLedgerDTO.builder()
                    .id(rs.getInt("id"))
                    .bookingId(rs.getObject("booking_id", Integer.class))
                    .bookingExternalId(rs.getString("external_booking_id"))
                    .guestName(rs.getString("guest_name"))
                    .ownerName(rs.getString("owner_name"))
                    .propertyName(rs.getString("property_name"))
                    .checkinDate(rs.getObject("checkin_date", LocalDate.class))
                    .checkoutDate(rs.getObject("checkout_date", LocalDate.class))
                    .documentNumber(rs.getString("document_number"))
                    .dataEvento(rs.getObject("data_evento", LocalDate.class))
                    .periodoMese(rs.getObject("periodo_mese", Integer.class))
                    .periodoAnno(rs.getObject("periodo_anno", Integer.class))
                    .canoneLocazione(rs.getBigDecimal("canone_locazione"))
                    .aliquotaRitenuta(rs.getBigDecimal("aliquota_ritenuta"))
                    .ritenutaAmount(rs.getBigDecimal("ritenuta_amount"))
                    .stato(rs.getString("stato"))
                    .fkF24RecordId(rs.getObject("fk_f24_record_id", Integer.class))
                    .build();

    private String loadSql(String classpath) {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile caricare SQL: " + classpath, e);
        }
    }

    /** Ritenute 'da_versare' del periodo non ancora agganciate ad alcun F24. */
    public List<WithholdingLedger> findDaVersareByPeriodo(Integer tenantId, Integer mese, Integer anno) {
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND periodo_mese = ? AND periodo_anno = ? " +
                "AND stato = 'da_versare' AND fk_f24_record_id IS NULL ORDER BY id";
        List<WithholdingLedger> result = jdbcTemplate.query(sql, rowMapper, tenantId, mese, anno);
        log.debug("WithholdingLedgerDAO.findDaVersareByPeriodo() - trovate={}", result.size());
        return result;
    }

    public WithholdingLedger insert(WithholdingLedger ledger) {
        String sql = "INSERT INTO withholding_ledger (" +
                "fk_tenant_id, fk_owner_id, fk_booking_id, fk_fiscal_document_id, " +
                "periodo_mese, periodo_anno, canone_locazione, aliquota_ritenuta, ritenuta_amount, " +
                "data_evento, stato, fk_f24_record_id, fk_ndc_id, fk_ledger_origine_id" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, ledger.getFkTenantId());
            ps.setObject(2, ledger.getFkOwnerId());
            ps.setObject(3, ledger.getFkBookingId());
            ps.setObject(4, ledger.getFkFiscalDocumentId());
            ps.setObject(5, ledger.getPeriodoMese());
            ps.setObject(6, ledger.getPeriodoAnno());
            ps.setObject(7, ledger.getCanoneLocazione());
            ps.setObject(8, ledger.getAliquotaRitenuta());
            ps.setObject(9, ledger.getRitenutaAmount());
            ps.setObject(10, ledger.getDataEvento());
            ps.setString(11, ledger.getStato() != null ? ledger.getStato() : "da_versare");
            ps.setObject(12, ledger.getFkF24RecordId());
            ps.setObject(13, ledger.getFkNdcId());
            ps.setObject(14, ledger.getFkLedgerOrigineId());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("WithholdingLedgerDAO.insert() - tenantId={} bookingId={} documentId={} ritenuta={}",
                ledger.getFkTenantId(), ledger.getFkBookingId(), ledger.getFkFiscalDocumentId(), ledger.getRitenutaAmount());
        return findById(id).orElseThrow();
    }

    /**
     * Righe di ledger di un owner per periodo (mese/anno), da cui il settlement ricava
     * totali e prenotazioni collegate.
     * Nessun filtro su {@code stato}: il versamento della ritenuta (F24) è indipendente
     * dalla liquidazione all'owner, quindi anche una ritenuta già 'versata' va liquidata.
     */
    public List<WithholdingLedger> findByOwnerAndPeriodo(Integer tenantId, Integer ownerId,
                                                          Integer mese, Integer anno) {
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? " +
                "AND periodo_mese = ? AND periodo_anno = ?" + SENZA_NDC + " ORDER BY id";
        List<WithholdingLedger> result = jdbcTemplate.query(sql, rowMapper, tenantId, ownerId, mese, anno);
        log.debug("WithholdingLedgerDAO.findByOwnerAndPeriodo() - tenantId={} ownerId={} periodo={}/{} righe={}",
                tenantId, ownerId, mese, anno, result.size());
        return result;
    }

    /**
     * Arretrati di un owner: righe di ledger di periodi PRECEDENTI a quello indicato
     * il cui booking non è ancora incluso in alcuna liquidazione. Servono al rollover,
     * perché una prenotazione fatturata dopo la chiusura del suo periodo (o dopo che il
     * settlement è stato pagato) non potrebbe più rientrarvi.
     * <p>
     * {@code excludeSettlementId} è il settlement che il chiamante sta ricalcolando: i suoi
     * collegamenti vengono ignorati perché stanno per essere riscritti. Senza questa
     * esclusione un arretrato già confluito in quel settlement non risulterebbe più
     * arretrato e il ricalcolo lo espellerebbe. Per un settlement nuovo si passa un id
     * inesistente (-1), così nessun collegamento viene ignorato.
     */
    public List<WithholdingLedger> findArretratiNonLiquidati(Integer tenantId, Integer ownerId,
                                                              Integer meseCorrente, Integer annoCorrente,
                                                              Integer excludeSettlementId) {
        String sql = SELECT_ALL + " wl WHERE wl.fk_tenant_id = ? AND wl.fk_owner_id = ? " +
                "AND (wl.periodo_anno < ? OR (wl.periodo_anno = ? AND wl.periodo_mese < ?)) " +
                "AND NOT EXISTS (SELECT 1 FROM settlement_booking sb " +
                "WHERE sb.fk_booking_id = wl.fk_booking_id AND sb.fk_settlement_id <> ?)" + SENZA_NDC_WL +
                " ORDER BY wl.periodo_anno, wl.periodo_mese, wl.id";
        List<WithholdingLedger> result = jdbcTemplate.query(sql, rowMapper,
                tenantId, ownerId, annoCorrente, annoCorrente, meseCorrente,
                excludeSettlementId != null ? excludeSettlementId : -1);
        log.info("WithholdingLedgerDAO.findArretratiNonLiquidati() - tenantId={} ownerId={} periodo={}/{} arretrati={}",
                tenantId, ownerId, meseCorrente, annoCorrente, result.size());
        return result;
    }

    /**
     * Owner con almeno un arretrato non liquidato rispetto al periodo indicato: completano
     * la lista degli owner da elaborare nel calcolo settlement, che altrimenti considererebbe
     * solo chi ha ritenute nel periodo scelto lasciando gli arretrati orfani per sempre.
     */
    public List<Integer> findDistinctOwnerIdsConArretrati(Integer tenantId, Integer meseCorrente, Integer annoCorrente) {
        String sql = "SELECT DISTINCT wl.fk_owner_id FROM withholding_ledger wl " +
                "WHERE wl.fk_tenant_id = ? " +
                "AND (wl.periodo_anno < ? OR (wl.periodo_anno = ? AND wl.periodo_mese < ?)) " +
                "AND NOT EXISTS (SELECT 1 FROM settlement_booking sb " +
                "WHERE sb.fk_booking_id = wl.fk_booking_id)" + SENZA_NDC_WL +
                " ORDER BY wl.fk_owner_id";
        List<Integer> result = jdbcTemplate.queryForList(sql, Integer.class,
                tenantId, annoCorrente, annoCorrente, meseCorrente);
        log.debug("WithholdingLedgerDAO.findDistinctOwnerIdsConArretrati() - tenantId={} periodo={}/{} owner={}",
                tenantId, meseCorrente, annoCorrente, result.size());
        return result;
    }

    /**
     * Canone e ritenuta dell'anno per un singolo immobile di un owner: alimenta il quadro
     * "Locazioni brevi" della CU, che va dettagliato per immobile.
     * Il ledger non ha la FK all'immobile, si passa dai booking di quella property.
     */
    public Map<String, Object> aggregaByOwnerPropertyAndAnno(Integer tenantId, Integer ownerId,
                                                             Integer propertyId, Integer anno) {
        log.debug("WithholdingLedgerDAO.aggregaByOwnerPropertyAndAnno() - tenantId={}, ownerId={}, propertyId={}, anno={}",
                tenantId, ownerId, propertyId, anno);
        String sql = "SELECT COALESCE(SUM(canone_locazione), 0) AS importo, " +
                "COALESCE(SUM(ritenuta_amount), 0) AS ritenuta, COUNT(id) AS num_righe " +
                "FROM withholding_ledger " +
                "WHERE fk_tenant_id = ? AND fk_owner_id = ? AND periodo_anno = ? " +
                "AND fk_booking_id IN (SELECT id FROM booking WHERE fk_property_id = ?)" + SENZA_NDC;
        return jdbcTemplate.queryForMap(sql, tenantId, ownerId, anno, propertyId);
    }

    /** Owner con almeno una ritenuta nel periodo (per il batch di calcolo settlement). */
    public List<Integer> findDistinctOwnerIdsByPeriodo(Integer tenantId, Integer mese, Integer anno) {
        log.debug("WithholdingLedgerDAO.findDistinctOwnerIdsByPeriodo() - tenantId={}, mese={}, anno={}", tenantId, mese, anno);
        String sql = "SELECT DISTINCT fk_owner_id FROM withholding_ledger " +
                "WHERE fk_tenant_id = ? AND periodo_mese = ? AND periodo_anno = ?" + SENZA_NDC + " ORDER BY fk_owner_id";
        return jdbcTemplate.queryForList(sql, Integer.class, tenantId, mese, anno);
    }

    public int updateF24Record(Integer id, Integer fkF24RecordId) {
        log.debug("WithholdingLedgerDAO.updateF24Record() - id={}, fkF24RecordId={}", id, fkF24RecordId);
        String sql = "UPDATE withholding_ledger SET fk_f24_record_id = ? WHERE id = ?";
        return jdbcTemplate.update(sql, fkF24RecordId, id);
    }

    public int updateStato(Integer id, String stato) {
        log.debug("WithholdingLedgerDAO.updateStato() - id={}, stato={}", id, stato);
        String sql = "UPDATE withholding_ledger SET stato = ? WHERE id = ?";
        return jdbcTemplate.update(sql, stato, id);
    }

    /**
     * Sgancia dall'F24 tutte le ritenute collegate e le riporta allo stato indicato.
     * È l'inverso di quanto fa F24Service.generaF24(): serve al cleanup dei test, che
     * altrimenti lascerebbe le ritenute 'versata' e senza F24, quindi non più
     * agganciabili da una generazione successiva.
     */
    public int resetF24Record(Integer f24RecordId, String stato) {
        log.debug("WithholdingLedgerDAO.resetF24Record() - f24RecordId={}, stato={}", f24RecordId, stato);
        String sql = "UPDATE withholding_ledger SET fk_f24_record_id = NULL, stato = ? WHERE fk_f24_record_id = ?";
        return jdbcTemplate.update(sql, stato, f24RecordId);
    }

    /** Ritenute registrate per il proprietario (FK RESTRICT su owner_profile). */
    public int countByOwner(Integer ownerId, Integer tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withholding_ledger WHERE fk_owner_id = ? AND fk_tenant_id = ?",
                Integer.class, ownerId, tenantId);
        return count != null ? count : 0;
    }

    // ── note di credito (migration 026) ──────────────────────────────────────

    /** Righe collegate a una NDC: stornate, crediti e ritenute versate di un booking stornato. */
    public List<WithholdingLedger> findByNdcId(Integer ndcId) {
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_ndc_id = ? ORDER BY id", rowMapper, ndcId);
    }

    /**
     * Storno della ritenuta da NDC: stato, collegamento alla NDC e, se richiesto, sgancio
     * dall'F24 non pagato in cui era confluita.
     */
    public int updateStorno(Integer id, String stato, Integer ndcId, boolean sganciaF24) {
        log.info("WithholdingLedgerDAO.updateStorno() - id={} stato={} ndcId={} sganciaF24={}", id, stato, ndcId, sganciaF24);
        return jdbcTemplate.update(
                "UPDATE withholding_ledger SET stato = ?, fk_ndc_id = ?, " +
                (sganciaF24 ? "fk_f24_record_id = NULL, " : "") +
                "updated_at = NOW() WHERE id = ?",
                stato, ndcId, id);
    }

    /** Annullamento NDC: toglie il collegamento alla NDC e ripristina lo stato. */
    public int ripristinaDaNdc(Integer id, String stato) {
        log.info("WithholdingLedgerDAO.ripristinaDaNdc() - id={} stato={}", id, stato);
        return jdbcTemplate.update(
                "UPDATE withholding_ledger SET stato = ?, fk_ndc_id = NULL, updated_at = NOW() WHERE id = ?",
                stato, id);
    }

    public int deleteById(Integer id) {
        log.info("WithholdingLedgerDAO.deleteById() - id={}", id);
        return jdbcTemplate.update("DELETE FROM withholding_ledger WHERE id = ?", id);
    }

    /**
     * Canone e ritenuta delle prenotazioni ancora collegate al settlement, con le stesse
     * esclusioni del calcolo (righe toccate da NDC escluse): usato dal ricalcolo dei totali
     * dopo la rimozione di un booking stornato.
     */
    public Map<String, Object> sommaPerSettlement(Integer settlementId) {
        String sql = "SELECT COALESCE(SUM(wl.canone_locazione), 0) AS canone, " +
                "COALESCE(SUM(wl.ritenuta_amount), 0) AS ritenuta " +
                "FROM withholding_ledger wl " +
                "WHERE wl.fk_booking_id IN (SELECT sb.fk_booking_id FROM settlement_booking sb " +
                "WHERE sb.fk_settlement_id = ?)" + SENZA_NDC_WL;
        return jdbcTemplate.queryForMap(sql, settlementId);
    }

    // ── crediti d'imposta e compensazione F24 (migration 027) ─────────────────

    /** Crediti ancora da usare: righe 'credito_imposta' (ritenuta negativa = residuo). */
    public List<WithholdingLedger> findCreditiDisponibili(Integer tenantId) {
        return jdbcTemplate.query(SELECT_ALL + " WHERE stato = 'credito_imposta' AND fk_tenant_id = ? " +
                "ORDER BY created_at, id", rowMapper, tenantId);
    }

    /** Righe di compensazione usate in un F24. */
    public List<WithholdingLedger> findCompensatiByF24(Integer f24RecordId) {
        return jdbcTemplate.query(SELECT_ALL + " WHERE stato = 'compensato' AND fk_f24_record_id = ? ORDER BY id",
                rowMapper, f24RecordId);
    }

    /** Compensazione totale: la riga di credito diventa 'compensato' nell'F24. */
    public int compensaTotale(Integer id, Integer f24RecordId) {
        log.info("WithholdingLedgerDAO.compensaTotale() - id={} f24={}", id, f24RecordId);
        return jdbcTemplate.update("UPDATE withholding_ledger SET stato = 'compensato', fk_f24_record_id = ?, " +
                "updated_at = NOW() WHERE id = ? AND stato = 'credito_imposta'", f24RecordId, id);
    }

    /** Aggiorna l'importo (negativo) di una riga di credito: residuo dopo spezzamento o ricongiunzione. */
    public int updateRitenutaAmount(Integer id, java.math.BigDecimal ritenutaAmount) {
        log.info("WithholdingLedgerDAO.updateRitenutaAmount() - id={} ritenuta={}", id, ritenutaAmount);
        return jdbcTemplate.update("UPDATE withholding_ledger SET ritenuta_amount = ?, updated_at = NOW() WHERE id = ?",
                ritenutaAmount, id);
    }

    /** Annulla una compensazione totale: la riga torna credito disponibile. */
    public int ripristinaCredito(Integer id) {
        log.info("WithholdingLedgerDAO.ripristinaCredito() - id={}", id);
        return jdbcTemplate.update("UPDATE withholding_ledger SET stato = 'credito_imposta', fk_f24_record_id = NULL, " +
                "updated_at = NOW() WHERE id = ?", id);
    }

    /** Righe 'compensato' collegate a una NDC: se presenti la NDC non è più annullabile. */
    public int countCompensatiByNdc(Integer ndcId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withholding_ledger WHERE fk_ndc_id = ? AND stato = 'compensato'",
                Integer.class, ndcId);
        return count != null ? count : 0;
    }

    /** Tutte le righe del tenant: filtri della lista documenti (stato fiscale delle ricevute). */
    public List<WithholdingLedger> findByTenantId(Integer tenantId) {
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY id", rowMapper, tenantId);
    }
}
