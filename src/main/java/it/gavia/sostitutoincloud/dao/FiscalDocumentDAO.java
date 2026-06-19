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
            "SELECT id, fk_tenant_id, fk_booking_id, fk_tipo_documento_id, fk_sdi_esito_id, " +
            "document_number, issue_date, recipient_name, recipient_tax_code, " +
            "total_amount, vat_amount, imponibile, ritenuta_amount, bollo_amount, iva_amount, " +
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

    public List<FiscalDocument> findByBookingId(Integer bookingId) {
        log.debug("FiscalDocumentDAO.findByBookingId() - bookingId={}", bookingId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_booking_id = ? ORDER BY created_at DESC", fiscalDocumentRowMapper, bookingId);
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
        String sql = "SELECT COUNT(*) + 1 FROM fiscal_document " +
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
                "fk_tenant_id, fk_booking_id, fk_tipo_documento_id, fk_sdi_esito_id, " +
                "document_number, issue_date, recipient_name, recipient_tax_code, " +
                "total_amount, vat_amount, imponibile, ritenuta_amount, bollo_amount, iva_amount, " +
                "fk_stato_documento_id, sdi_identifier" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, doc.getFkTenantId());
            ps.setObject(2, doc.getFkBookingId());
            ps.setObject(3, doc.getFkTipoDocumentoId());
            ps.setObject(4, doc.getFkSdiEsitoId());
            ps.setString(5, doc.getDocumentNumber());
            ps.setObject(6, doc.getIssueDate());
            ps.setString(7, doc.getRecipientName());
            ps.setString(8, doc.getRecipientTaxCode());
            ps.setObject(9, doc.getTotalAmount());
            ps.setObject(10, doc.getVatAmount());
            ps.setObject(11, doc.getImponibile());
            ps.setObject(12, doc.getRitenutaAmount());
            ps.setObject(13, doc.getBolloAmount());
            ps.setObject(14, doc.getIvaAmount());
            ps.setObject(15, doc.getFkStatoDocumentoId());
            ps.setString(16, doc.getSdiIdentifier());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("FiscalDocumentDAO.insert() - number={} tenantId={}", doc.getDocumentNumber(), doc.getFkTenantId());
        return findById(id).orElseThrow();
    }
}
