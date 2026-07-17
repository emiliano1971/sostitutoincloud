package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.ImportTemplateRowMapper;
import it.gavia.sostitutoincloud.model.ImportTemplate;
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
public class ImportTemplateDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, nome, descrizione, header_row, " +
            "booking_mapping, guest_mapping, created_at, updated_at " +
            "FROM import_template";

    private final JdbcTemplate jdbcTemplate;
    private final ImportTemplateRowMapper rowMapper = new ImportTemplateRowMapper();

    public ImportTemplateDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ImportTemplate> findByTenantId(Integer tenantId) {
        log.debug("ImportTemplateDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(
                SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY nome",
                rowMapper, tenantId);
    }

    public Optional<ImportTemplate> findById(Integer id) {
        log.debug("ImportTemplateDAO.findById() - id={}", id);
        List<ImportTemplate> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE id = ?", rowMapper, id);
        return result.stream().findFirst();
    }

    public ImportTemplate insert(ImportTemplate t) {
        String sql = "INSERT INTO import_template " +
                     "(fk_tenant_id, nome, descrizione, header_row, booking_mapping, guest_mapping) " +
                     "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, t.getFkTenantId());
            ps.setString(2, t.getNome());
            ps.setString(3, t.getDescrizione());
            ps.setObject(4, t.getHeaderRow() != null ? t.getHeaderRow() : 0);
            ps.setString(5, t.getBookingMapping() != null ? t.getBookingMapping() : "{}");
            ps.setString(6, t.getGuestMapping() != null ? t.getGuestMapping() : "{}");
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("ImportTemplateDAO.insert() - id={}", id);
        return findById(id).orElseThrow();
    }

    public ImportTemplate update(ImportTemplate t) {
        jdbcTemplate.update(
                "UPDATE import_template SET nome = ?, descrizione = ?, header_row = ?, " +
                "booking_mapping = ?::jsonb, guest_mapping = ?::jsonb, updated_at = NOW() " +
                "WHERE id = ? AND fk_tenant_id = ?",
                t.getNome(),
                t.getDescrizione(),
                t.getHeaderRow() != null ? t.getHeaderRow() : 0,
                t.getBookingMapping() != null ? t.getBookingMapping() : "{}",
                t.getGuestMapping() != null ? t.getGuestMapping() : "{}",
                t.getId(),
                t.getFkTenantId());
        log.info("ImportTemplateDAO.update() - id={}", t.getId());
        return findById(t.getId()).orElseThrow();
    }

    public void delete(Integer id, Integer tenantId) {
        log.info("ImportTemplateDAO.delete() - id={}", id);
        jdbcTemplate.update(
                "DELETE FROM import_template WHERE id = ? AND fk_tenant_id = ?",
                id, tenantId);
    }
}
