package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.WithholdingLedgerRowMapper;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j2
@Repository
public class WithholdingLedgerDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, fk_booking_id, fk_fiscal_document_id, " +
            "periodo_mese, periodo_anno, canone_locazione, aliquota_ritenuta, ritenuta_amount, " +
            "data_evento, stato, fk_f24_record_id, created_at, updated_at FROM withholding_ledger";

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
                "data_evento, stato, fk_f24_record_id" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
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
                "AND periodo_mese = ? AND periodo_anno = ? ORDER BY id";
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
                "WHERE sb.fk_booking_id = wl.fk_booking_id AND sb.fk_settlement_id <> ?) " +
                "ORDER BY wl.periodo_anno, wl.periodo_mese, wl.id";
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
                "WHERE sb.fk_booking_id = wl.fk_booking_id) " +
                "ORDER BY wl.fk_owner_id";
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
                "AND fk_booking_id IN (SELECT id FROM booking WHERE fk_property_id = ?)";
        return jdbcTemplate.queryForMap(sql, tenantId, ownerId, anno, propertyId);
    }

    /** Owner con almeno una ritenuta nel periodo (per il batch di calcolo settlement). */
    public List<Integer> findDistinctOwnerIdsByPeriodo(Integer tenantId, Integer mese, Integer anno) {
        log.debug("WithholdingLedgerDAO.findDistinctOwnerIdsByPeriodo() - tenantId={}, mese={}, anno={}", tenantId, mese, anno);
        String sql = "SELECT DISTINCT fk_owner_id FROM withholding_ledger " +
                "WHERE fk_tenant_id = ? AND periodo_mese = ? AND periodo_anno = ? ORDER BY fk_owner_id";
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
}
