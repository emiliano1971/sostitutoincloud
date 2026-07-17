package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.CuRecordRowMapper;
import it.gavia.sostitutoincloud.model.CuRecord;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j2
@Repository
public class CuRecordDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, tax_year, total_compensi, total_ritenute, " +
            "total_imponibile, stato, generated_at, sent_at, created_at, updated_at FROM cu_record";

    private final JdbcTemplate jdbcTemplate;
    private final CuRecordRowMapper cuRecordRowMapper = new CuRecordRowMapper();

    public CuRecordDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CuRecord> findAll() {
        List<CuRecord> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", cuRecordRowMapper);
        log.debug("CuRecordDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<CuRecord> findById(Integer id) {
        log.debug("CuRecordDAO.findById() - id={}", id);
        List<CuRecord> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", cuRecordRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<CuRecord> findByTenantId(Integer tenantId) {
        log.debug("CuRecordDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY tax_year DESC", cuRecordRowMapper, tenantId);
    }

    public List<CuRecord> findByOwnerId(Integer ownerId) {
        log.debug("CuRecordDAO.findByOwnerId() - ownerId={}", ownerId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_owner_id = ? ORDER BY tax_year DESC", cuRecordRowMapper, ownerId);
    }

    public List<CuRecord> findByTenantIdAndOwnerId(Integer tenantId, Integer ownerId) {
        log.debug("CuRecordDAO.findByTenantIdAndOwnerId() - tenantId={}, ownerId={}", tenantId, ownerId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? ORDER BY tax_year DESC";
        return jdbcTemplate.query(sql, cuRecordRowMapper, tenantId, ownerId);
    }

    public List<CuRecord> findByTenantIdAndTaxYear(Integer tenantId, Integer taxYear) {
        log.debug("CuRecordDAO.findByTenantIdAndTaxYear() - tenantId={}, taxYear={}", tenantId, taxYear);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND tax_year = ? ORDER BY id";
        return jdbcTemplate.query(sql, cuRecordRowMapper, tenantId, taxYear);
    }

    public List<CuRecord> findByTenantIdAndStatus(Integer tenantId, String status) {
        log.debug("CuRecordDAO.findByTenantIdAndStatus() - tenantId={}, status={}", tenantId, status);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND stato = ? ORDER BY tax_year DESC";
        return jdbcTemplate.query(sql, cuRecordRowMapper, tenantId, status);
    }

    // ── scrittura ─────────────────────────────────────────────────────────────

    public CuRecord insert(CuRecord cu) {
        String sql = "INSERT INTO cu_record (fk_tenant_id, fk_owner_id, tax_year, " +
                "total_compensi, total_imponibile, total_ritenute, stato, generated_at) VALUES (?,?,?,?,?,?,?,NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, cu.getFkTenantId());
            ps.setObject(2, cu.getFkOwnerId());
            ps.setObject(3, cu.getTaxYear());
            ps.setObject(4, cu.getTotalCompensi());
            ps.setObject(5, cu.getTotalImponibile());
            ps.setObject(6, cu.getTotalRitenute());
            ps.setObject(7, cu.getStato(), Types.OTHER);
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("CuRecordDAO.insert() - id={}", id);
        return findById(id).orElseThrow(() -> new RuntimeException("CU non trovata dopo insert: id=" + id));
    }

    public CuRecord updateTotaliAndStato(Integer id, BigDecimal totalCompensi, BigDecimal totalImponibile,
                                         BigDecimal totalRitenute, String stato) {
        String sql = "UPDATE cu_record SET total_compensi=?, total_imponibile=?, total_ritenute=?, " +
                "stato=?, generated_at=NOW(), updated_at=NOW() WHERE id=?";
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, totalCompensi);
            ps.setObject(2, totalImponibile);
            ps.setObject(3, totalRitenute);
            ps.setObject(4, stato, Types.OTHER);
            ps.setObject(5, id);
            return ps;
        });
        log.info("CuRecordDAO.updateTotaliAndStato() - id={}", id);
        return findById(id).orElseThrow(() -> new RuntimeException("CU non trovata: id=" + id));
    }

    public CuRecord updateStato(Integer id, String stato) {
        String sql = "UPDATE cu_record SET stato=?, updated_at=NOW() WHERE id=?";
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, stato, Types.OTHER);
            ps.setObject(2, id);
            return ps;
        });
        log.info("CuRecordDAO.updateStato() - id={} stato={}", id, stato);
        return findById(id).orElseThrow(() -> new RuntimeException("CU non trovata: id=" + id));
    }

    // ── aggregazione su withholding_ledger (SQL esternalizzato) ────────────────

    public Map<String, Object> aggregateByOwnerYear(Integer tenantId, Integer ownerId, Integer taxYear) {
        log.debug("CuRecordDAO.aggregateByOwnerYear() - tenantId={} ownerId={} year={}", tenantId, ownerId, taxYear);
        String sql = loadSql("sql/cu_record/aggregate_by_owner_year.sql");
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, tenantId, ownerId, taxYear);
        // SUM su zero righe restituisce comunque una riga con valori NULL: lo trattiamo come "nessuna ritenuta".
        if (row.get("total_ritenute") == null) {
            throw new IllegalArgumentException(
                    "Nessuna ritenuta trovata per owner " + ownerId + " anno " + taxYear);
        }
        return row;
    }

    public List<Integer> findOwnerIdsWithWithholding(Integer tenantId, Integer taxYear) {
        String sql = loadSql("sql/cu_record/owners_with_withholding.sql");
        List<Integer> result = jdbcTemplate.queryForList(sql, Integer.class, tenantId, taxYear);
        log.debug("CuRecordDAO.findOwnerIdsWithWithholding() - tenantId={} year={} trovati={}",
                tenantId, taxYear, result.size());
        return result;
    }

    private String loadSql(String classpath) {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile caricare SQL: " + classpath, e);
        }
    }
}
