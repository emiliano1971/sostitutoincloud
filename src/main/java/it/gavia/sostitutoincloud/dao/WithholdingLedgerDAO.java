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
     * Aggrega canone e ritenuta di un owner per periodo (mese/anno).
     * Restituisce sempre una riga (SUM/COUNT senza GROUP BY): total_amount e
     * withholding_amount sono NULL se non ci sono ritenute nel periodo.
     */
    public Map<String, Object> aggregaByOwnerAndPeriodo(Integer tenantId, Integer ownerId, Integer mese, Integer anno) {
        log.debug("WithholdingLedgerDAO.aggregaByOwnerAndPeriodo() - tenantId={}, ownerId={}, mese={}, anno={}",
                tenantId, ownerId, mese, anno);
        String sql = "SELECT SUM(canone_locazione) AS total_amount, " +
                "SUM(ritenuta_amount) AS withholding_amount, COUNT(id) AS num_righe " +
                "FROM withholding_ledger " +
                "WHERE fk_tenant_id = ? AND fk_owner_id = ? AND periodo_mese = ? AND periodo_anno = ?";
        return jdbcTemplate.queryForMap(sql, tenantId, ownerId, mese, anno);
    }

    /** fk_booking_id distinti delle ritenute di un owner nel periodo. */
    public List<Integer> findDistinctBookingIdsByOwnerAndPeriodo(Integer tenantId, Integer ownerId, Integer mese, Integer anno) {
        log.debug("WithholdingLedgerDAO.findDistinctBookingIdsByOwnerAndPeriodo() - tenantId={}, ownerId={}, mese={}, anno={}",
                tenantId, ownerId, mese, anno);
        String sql = "SELECT DISTINCT fk_booking_id FROM withholding_ledger " +
                "WHERE fk_tenant_id = ? AND fk_owner_id = ? AND periodo_mese = ? AND periodo_anno = ?";
        return jdbcTemplate.queryForList(sql, Integer.class, tenantId, ownerId, mese, anno);
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
}
