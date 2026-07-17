package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.F24RecordRowMapper;
import it.gavia.sostitutoincloud.model.F24Record;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class F24RecordDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_codice_tributo_id, period, total_amount, withholdings_count, " +
            "stato, deadline_date, payment_date, periodo_mese, periodo_anno, reference_year, " +
            "created_at, updated_at FROM f24_record";

    private final JdbcTemplate jdbcTemplate;
    private final F24RecordRowMapper f24RecordRowMapper = new F24RecordRowMapper();

    public F24RecordDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<F24Record> findAll() {
        List<F24Record> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", f24RecordRowMapper);
        log.debug("F24RecordDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<F24Record> findById(Integer id) {
        log.debug("F24RecordDAO.findById() - id={}", id);
        List<F24Record> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", f24RecordRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<F24Record> findByTenantId(Integer tenantId) {
        log.debug("F24RecordDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY period DESC", f24RecordRowMapper, tenantId);
    }

    public List<F24Record> findByTenantIdAndPeriod(Integer tenantId, String period) {
        log.debug("F24RecordDAO.findByTenantIdAndPeriod() - tenantId={}, period={}", tenantId, period);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND period = ? ORDER BY id";
        return jdbcTemplate.query(sql, f24RecordRowMapper, tenantId, period);
    }

    public List<F24Record> findByTenantIdAndStatus(Integer tenantId, String status) {
        log.debug("F24RecordDAO.findByTenantIdAndStatus() - tenantId={}, status={}", tenantId, status);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND stato = ? ORDER BY period DESC";
        return jdbcTemplate.query(sql, f24RecordRowMapper, tenantId, status);
    }

    /** Tutti gli F24 del tenant ordinati per periodo (anno/mese) decrescente. */
    public List<F24Record> findByTenant(Integer tenantId) {
        log.debug("F24RecordDAO.findByTenant() - tenantId={}", tenantId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY periodo_anno DESC, periodo_mese DESC, id DESC";
        return jdbcTemplate.query(sql, f24RecordRowMapper, tenantId);
    }

    /** F24 esistenti per tenant + periodo (anno/mese), per il controllo di unicità. */
    public List<F24Record> findByTenantAndPeriodo(Integer tenantId, Integer anno, Integer mese) {
        log.debug("F24RecordDAO.findByTenantAndPeriodo() - tenantId={}, anno={}, mese={}", tenantId, anno, mese);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND periodo_anno = ? AND periodo_mese = ? ORDER BY id";
        return jdbcTemplate.query(sql, f24RecordRowMapper, tenantId, anno, mese);
    }

    public F24Record insert(F24Record record) {
        String sql = "INSERT INTO f24_record (" +
                "fk_tenant_id, fk_codice_tributo_id, period, total_amount, withholdings_count, " +
                "stato, deadline_date, payment_date, periodo_mese, periodo_anno, reference_year" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, record.getFkTenantId());
            ps.setObject(2, record.getFkCodiceTributoId());
            ps.setString(3, record.getPeriod());
            ps.setObject(4, record.getTotalAmount());
            ps.setObject(5, record.getWithholdingsCount());
            ps.setObject(6, record.getStato(), Types.OTHER);
            ps.setObject(7, record.getDeadlineDate());
            ps.setObject(8, record.getPaymentDate());
            ps.setObject(9, record.getPeriodoMese());
            ps.setObject(10, record.getPeriodoAnno());
            ps.setObject(11, record.getReferenceYear());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("F24RecordDAO.insert() - periodo={}/{} importo={}",
                record.getPeriodoMese(), record.getPeriodoAnno(), record.getTotalAmount());
        return findById(id).orElseThrow(() -> new RuntimeException("F24 non trovato dopo insert: id=" + id));
    }

    public void updateTotale(Integer id, java.math.BigDecimal totalAmount, Integer withholdingsCount) {
        String sql = "UPDATE f24_record SET total_amount = ?, withholdings_count = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, totalAmount, withholdingsCount, id);
        log.info("F24RecordDAO.updateTotale() - id={} totale={} count={}", id, totalAmount, withholdingsCount);
    }

    public F24Record updateStato(Integer id, String stato, LocalDate paymentDate) {
        String sql = "UPDATE f24_record SET stato = ?, payment_date = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, stato, Types.OTHER);
            ps.setObject(2, paymentDate);
            ps.setObject(3, id);
            return ps;
        });
        log.info("F24RecordDAO.updateStato() - id={} stato={} paymentDate={}", id, stato, paymentDate);
        return findById(id).orElseThrow(() -> new RuntimeException("F24 non trovato: id=" + id));
    }
}
