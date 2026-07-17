package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.FiscalDocumentRowMapper;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class FiscalDocumentDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_booking_id, fk_owner_id, fk_tipo_documento_id, fk_sdi_esito_id, " +
            "document_number, issue_date, recipient_name, recipient_tax_code, " +
            "total_amount, vat_amount, aliquota_iva, imponibile, ritenuta_amount, bollo_amount, " +
            "canone_locazione, fk_documento_collegato_id, " +
            "fk_stato_documento_id, sdi_identifier, " +
            "created_at, updated_at FROM fiscal_document";

    private final JdbcTemplate jdbcTemplate;
    private final FiscalDocumentRowMapper fiscalDocumentRowMapper = new FiscalDocumentRowMapper();

    public FiscalDocumentDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FiscalDocument> findAll() {
        List<FiscalDocument> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", fiscalDocumentRowMapper);
        log.debug("FiscalDocumentDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<FiscalDocument> findById(Integer id) {
        log.debug("FiscalDocumentDAO.findById() - id={}", id);
        List<FiscalDocument> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", fiscalDocumentRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<FiscalDocument> findByTenantId(Integer tenantId) {
        log.debug("FiscalDocumentDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY id", fiscalDocumentRowMapper, tenantId);
    }

    public List<FiscalDocument> findByOwnerAndTenant(Integer tenantId, Integer ownerId) {
        log.debug("FiscalDocumentDAO.findByOwnerAndTenant() - tenantId={}, ownerId={}", tenantId, ownerId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? ORDER BY issue_date DESC";
        return jdbcTemplate.query(sql, fiscalDocumentRowMapper, tenantId, ownerId);
    }

    public List<FiscalDocument> findByBookingId(Integer bookingId) {
        log.debug("FiscalDocumentDAO.findByBookingId() - bookingId={}", bookingId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_booking_id = ? ORDER BY created_at DESC", fiscalDocumentRowMapper, bookingId);
    }

    public void deleteByBookingId(Integer bookingId) {
        log.debug("FiscalDocumentDAO.deleteByBookingId() - bookingId={}", bookingId);
        jdbcTemplate.update("DELETE FROM fiscal_document WHERE fk_booking_id = ?", bookingId);
    }

    public Optional<FiscalDocument> findByDocumentNumber(String documentNumber) {
        log.debug("FiscalDocumentDAO.findByDocumentNumber() - documentNumber={}", documentNumber);
        List<FiscalDocument> result = jdbcTemplate.query(SELECT_ALL + " WHERE document_number = ?", fiscalDocumentRowMapper, documentNumber);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<FiscalDocument> findByTenantIdAndStatoDocumentoId(Integer tenantId, Integer statoId) {
        log.debug("FiscalDocumentDAO.findByTenantIdAndStatoDocumentoId() - tenantId={}, statoId={}", tenantId, statoId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_stato_documento_id = ? ORDER BY id";
        return jdbcTemplate.query(sql, fiscalDocumentRowMapper, tenantId, statoId);
    }

    public List<FiscalDocument> findByTenantIdAndIssueDateBetween(Integer tenantId, LocalDate from, LocalDate to) {
        log.debug("FiscalDocumentDAO.findByTenantIdAndIssueDateBetween() - tenantId={}, from={}, to={}", tenantId, from, to);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND issue_date BETWEEN ? AND ? ORDER BY issue_date";
        return jdbcTemplate.query(sql, fiscalDocumentRowMapper, tenantId, from, to);
    }

    public Optional<FiscalDocument> findBySdiIdentifier(String sdiIdentifier) {
        log.debug("FiscalDocumentDAO.findBySdiIdentifier() - sdiIdentifier={}", sdiIdentifier);
        List<FiscalDocument> result = jdbcTemplate.query(SELECT_ALL + " WHERE sdi_identifier = ?", fiscalDocumentRowMapper, sdiIdentifier);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * Calcola il prossimo numero progressivo per tenant + tipo documento + anno (in base a issue_date).
     * NB: lo schema usa la colonna FK fk_tipo_documento_id (lookup tipo_documento), non una colonna enum.
     */
    public Integer getNextProgressiveNumber(Integer tenantId, Integer tipoDocumentoId, Integer anno) {
        log.debug("FiscalDocumentDAO.getNextProgressiveNumber() - tenantId={}, tipoDocumentoId={}, anno={}",
                tenantId, tipoDocumentoId, anno);
        // Usa MAX(progressivo)+1 anziché COUNT(*)+1: il count genera duplicati quando ci sono
        // documenti eliminati o numerazioni non contigue. Il progressivo è la terza parte del
        // document_number (es. 'FT-2026-0003' -> 3). COALESCE gestisce l'assenza di documenti -> 1.
        String sql = "SELECT COALESCE(MAX(CAST(SPLIT_PART(document_number, '-', 3) AS INTEGER)), 0) + 1 " +
                "FROM fiscal_document " +
                "WHERE fk_tenant_id = ? AND fk_tipo_documento_id = ? " +
                "AND EXTRACT(YEAR FROM issue_date) = ?";
        Integer next = jdbcTemplate.queryForObject(sql, Integer.class, tenantId, tipoDocumentoId, anno);
        return next != null ? next : 1;
    }

    /**
     * Genera il numero documento nel formato "{prefix}-{anno}-{numero:04d}", es. "RIC-2026-0003".
     * Il prefix dipende dal tipo logico (RIC per ricevuta, FT per fattura) ed è scelto dal Service.
     */
    public String generateDocumentNumber(Integer tenantId, Integer tipoDocumentoId, String prefix, Integer anno) {
        Integer numero = getNextProgressiveNumber(tenantId, tipoDocumentoId, anno);
        String documentNumber = String.format("%s-%d-%04d", prefix, anno, numero);
        log.debug("FiscalDocumentDAO.generateDocumentNumber() - tenantId={}, prefix={}, anno={}, number={}",
                tenantId, prefix, anno, documentNumber);
        return documentNumber;
    }

    public FiscalDocument insert(FiscalDocument doc) {
        String sql = "INSERT INTO fiscal_document (" +
                "fk_tenant_id, fk_booking_id, fk_owner_id, fk_tipo_documento_id, fk_sdi_esito_id, " +
                "document_number, issue_date, recipient_name, recipient_tax_code, " +
                "total_amount, vat_amount, aliquota_iva, imponibile, ritenuta_amount, bollo_amount, " +
                "canone_locazione, fk_documento_collegato_id, " +
                "fk_stato_documento_id, sdi_identifier" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, doc.getFkTenantId());
            ps.setObject(2, doc.getFkBookingId());
            ps.setObject(3, doc.getFkOwnerId());
            ps.setObject(4, doc.getFkTipoDocumentoId());
            ps.setObject(5, doc.getFkSdiEsitoId());
            ps.setString(6, doc.getDocumentNumber());
            ps.setObject(7, doc.getIssueDate());
            ps.setString(8, doc.getRecipientName());
            ps.setString(9, doc.getRecipientTaxCode());
            ps.setObject(10, doc.getTotalAmount());
            ps.setObject(11, doc.getVatAmount());
            ps.setObject(12, doc.getAliquotaIva());
            ps.setObject(13, doc.getImponibile());
            ps.setObject(14, doc.getRitenutaAmount());
            ps.setObject(15, doc.getBolloAmount());
            ps.setObject(16, doc.getCanoneLocazione());
            ps.setObject(17, doc.getFkDocumentoCollegatoId());
            ps.setObject(18, doc.getFkStatoDocumentoId());
            ps.setString(19, doc.getSdiIdentifier());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("FiscalDocumentDAO.insert() - number={} tenantId={}", doc.getDocumentNumber(), doc.getFkTenantId());
        return findById(id).orElseThrow();
    }

    /**
     * Aggiorna il collegamento tra documenti dello stesso booking (ricevuta <-> fattura PM).
     */
    public void updateDocumentoCollegato(Integer id, Integer fkDocumentoCollegatoId) {
        log.debug("FiscalDocumentDAO.updateDocumentoCollegato() - id={}, collegatoId={}", id, fkDocumentoCollegatoId);
        jdbcTemplate.update("UPDATE fiscal_document SET fk_documento_collegato_id = ? WHERE id = ?",
                fkDocumentoCollegatoId, id);
    }

    /**
     * Aggiorna lo stato (lookup stato_documento) di un documento fiscale.
     */
    public void updateStato(Integer id, Integer fkStatoDocumentoId) {
        log.debug("FiscalDocumentDAO.updateStato() - id={}, statoId={}", id, fkStatoDocumentoId);
        jdbcTemplate.update("UPDATE fiscal_document SET fk_stato_documento_id = ?, updated_at = NOW() WHERE id = ?",
                fkStatoDocumentoId, id);
    }
}
