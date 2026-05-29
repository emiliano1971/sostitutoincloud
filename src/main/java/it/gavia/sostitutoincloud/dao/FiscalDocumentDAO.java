package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.FiscalDocumentRowMapper;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class FiscalDocumentDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_booking_id, fk_tipo_documento_id, fk_sdi_esito_id, " +
            "document_number, issue_date, recipient_name, recipient_tax_code, " +
            "total_amount, vat_amount, fk_stato_documento_id, sdi_identifier, " +
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

    public List<FiscalDocument> findByBookingId(Integer bookingId) {
        log.debug("FiscalDocumentDAO.findByBookingId() - bookingId={}", bookingId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_booking_id = ? ORDER BY id", fiscalDocumentRowMapper, bookingId);
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
}
