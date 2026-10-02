package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.FiscalDocumentRigaNdcRowMapper;
import it.gavia.sostitutoincloud.model.FiscalDocumentRigaNdc;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class FiscalDocumentRigaNdcDAO {

    private static final String COLS =
            "r.id, r.fk_fiscal_document_id, r.fk_tenant_id, r.fk_split_economico_id, r.descrizione, " +
            "r.importo_stornato, r.imponibile_stornato, r.aliquota_iva, r.ordinamento, " +
            "r.created_at, r.updated_at, r.created_by";

    private final JdbcTemplate jdbcTemplate;
    private final FiscalDocumentRigaNdcRowMapper rowMapper = new FiscalDocumentRigaNdcRowMapper();

    public FiscalDocumentRigaNdcDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<FiscalDocumentRigaNdc> findById(Integer id) {
        List<FiscalDocumentRigaNdc> result = jdbcTemplate.query(
                "SELECT " + COLS + " FROM fiscal_document_riga_ndc r WHERE r.id = ?", rowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<FiscalDocumentRigaNdc> findByFiscalDocumentId(Integer docId) {
        log.debug("FiscalDocumentRigaNdcDAO.findByFiscalDocumentId() - docId={}", docId);
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM fiscal_document_riga_ndc r " +
                "WHERE r.fk_fiscal_document_id = ? ORDER BY r.ordinamento, r.id",
                rowMapper, docId);
    }

    /** Righe delle NDC non annullate del booking (tipo 'nota_credito', stato != 'annullata'). */
    public List<FiscalDocumentRigaNdc> findByBookingId(Integer bookingId) {
        log.debug("FiscalDocumentRigaNdcDAO.findByBookingId() - bookingId={}", bookingId);
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM fiscal_document_riga_ndc r " +
                "JOIN fiscal_document d ON d.id = r.fk_fiscal_document_id " +
                "JOIN tipo_documento t ON t.id = d.fk_tipo_documento_id " +
                "JOIN stato_documento s ON s.id = d.fk_stato_documento_id " +
                "WHERE d.fk_booking_id = ? AND t.codice = 'nota_credito' AND s.codice <> 'annullata' " +
                "ORDER BY d.id, r.ordinamento, r.id",
                rowMapper, bookingId);
    }

    public FiscalDocumentRigaNdc insert(FiscalDocumentRigaNdc riga) {
        String sql = "INSERT INTO fiscal_document_riga_ndc " +
                "(fk_fiscal_document_id, fk_tenant_id, fk_split_economico_id, descrizione, " +
                "importo_stornato, imponibile_stornato, aliquota_iva, ordinamento, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, riga.getFkFiscalDocumentId());
            ps.setObject(2, riga.getFkTenantId());
            ps.setObject(3, riga.getFkSplitEconomicoId());
            ps.setString(4, riga.getDescrizione());
            ps.setObject(5, riga.getImportoStornato());
            ps.setObject(6, riga.getImponibileStornato());
            ps.setObject(7, riga.getAliquotaIva());
            ps.setObject(8, riga.getOrdinamento() != null ? riga.getOrdinamento() : 0);
            ps.setObject(9, riga.getCreatedBy());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("FiscalDocumentRigaNdcDAO.insert() - id={} docId={}", id, riga.getFkFiscalDocumentId());
        return findById(id).orElseThrow();
    }

    /** Usato dall'annullamento della NDC. */
    public int deleteByFiscalDocumentId(Integer docId) {
        int righe = jdbcTemplate.update("DELETE FROM fiscal_document_riga_ndc WHERE fk_fiscal_document_id = ?", docId);
        log.info("FiscalDocumentRigaNdcDAO.deleteByFiscalDocumentId() - docId={} eliminate={}", docId, righe);
        return righe;
    }
}
