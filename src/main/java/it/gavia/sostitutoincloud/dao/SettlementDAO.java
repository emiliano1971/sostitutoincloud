package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.SettlementRowMapper;
import it.gavia.sostitutoincloud.model.Settlement;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class SettlementDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, period, total_amount, withholding_amount, " +
            "net_amount, stato, payment_date, periodo_mese, periodo_anno, " +
            "created_at, updated_at FROM settlement";

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbcTemplate;
    private final SettlementRowMapper settlementRowMapper = new SettlementRowMapper();

    public SettlementDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Settlement> findAll() {
        List<Settlement> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", settlementRowMapper);
        log.debug("SettlementDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<Settlement> findById(Integer id) {
        log.debug("SettlementDAO.findById() - id={}", id);
        List<Settlement> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", settlementRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<Settlement> findByTenantId(Integer tenantId) {
        log.debug("SettlementDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY period DESC", settlementRowMapper, tenantId);
    }

    public List<Settlement> findByOwnerId(Integer ownerId) {
        log.debug("SettlementDAO.findByOwnerId() - ownerId={}", ownerId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_owner_id = ? ORDER BY period DESC", settlementRowMapper, ownerId);
    }

    public List<Settlement> findByTenantIdAndOwnerId(Integer tenantId, Integer ownerId) {
        log.debug("SettlementDAO.findByTenantIdAndOwnerId() - tenantId={}, ownerId={}", tenantId, ownerId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? ORDER BY period DESC";
        return jdbcTemplate.query(sql, settlementRowMapper, tenantId, ownerId);
    }

    public List<Settlement> findByTenantIdAndStatus(Integer tenantId, String status) {
        log.debug("SettlementDAO.findByTenantIdAndStatus() - tenantId={}, status={}", tenantId, status);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND stato = ? ORDER BY period DESC";
        return jdbcTemplate.query(sql, settlementRowMapper, tenantId, status);
    }

    public Optional<Settlement> findByOwnerAndPeriod(Integer tenantId, Integer ownerId, String period) {
        log.debug("SettlementDAO.findByOwnerAndPeriod() - tenantId={}, ownerId={}, period={}", tenantId, ownerId, period);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? AND period = ?";
        List<Settlement> result = jdbcTemplate.query(sql, settlementRowMapper, tenantId, ownerId, period);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Settlement insert(Settlement s) {
        String sql = "INSERT INTO settlement (fk_tenant_id, fk_owner_id, period, periodo_mese, periodo_anno, " +
                "total_amount, withholding_amount, net_amount, stato) VALUES (?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, s.getFkTenantId());
            ps.setObject(2, s.getFkOwnerId());
            ps.setString(3, s.getPeriod());
            ps.setObject(4, s.getPeriodoMese());
            ps.setObject(5, s.getPeriodoAnno());
            ps.setObject(6, s.getTotalAmount());
            ps.setObject(7, s.getWithholdingAmount());
            ps.setObject(8, s.getNetAmount());
            ps.setObject(9, s.getStato(), Types.OTHER); // cast String → enum settlement_status
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("SettlementDAO.insert() - id={}", id);
        return findById(id).orElseThrow();
    }

    public Settlement updateTotali(Integer id, BigDecimal totalAmount, BigDecimal withholdingAmount, BigDecimal netAmount) {
        String sql = "UPDATE settlement SET total_amount = ?, withholding_amount = ?, net_amount = ?, " +
                "stato = 'calculated', updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, totalAmount, withholdingAmount, netAmount, id);
        log.info("SettlementDAO.updateTotali() - id={}", id);
        return findById(id).orElseThrow();
    }

    public Settlement updateStato(Integer id, String stato) {
        // Se lo stato passa a 'paid' valorizziamo anche la data di pagamento.
        String sql = "paid".equals(stato)
                ? "UPDATE settlement SET stato = ?, payment_date = NOW(), updated_at = NOW() WHERE id = ?"
                : "UPDATE settlement SET stato = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, stato, Types.OTHER); // cast String → enum settlement_status
            ps.setObject(2, id);
            return ps;
        });
        log.info("SettlementDAO.updateStato() - id={} stato={}", id, stato);
        return findById(id).orElseThrow();
    }

    /** Trova liquidazioni il cui periodo cade nell'intervallo [from, to] (formato YYYY-MM). */
    public List<Settlement> findByPeriodOverlap(Integer tenantId, Integer ownerId, LocalDate from, LocalDate to) {
        log.debug("SettlementDAO.findByPeriodOverlap() - tenantId={}, ownerId={}, from={}, to={}", tenantId, ownerId, from, to);
        String fromPeriod = from.format(PERIOD_FMT);
        String toPeriod = to.format(PERIOD_FMT);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? AND period >= ? AND period <= ? ORDER BY period";
        return jdbcTemplate.query(sql, settlementRowMapper, tenantId, ownerId, fromPeriod, toPeriod);
    }
}
