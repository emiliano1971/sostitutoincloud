package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TassaStagioneRowMapper;
import it.gavia.sostitutoincloud.model.TassaStagione;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Repository
public class TassaStagioneDAO {

    private final JdbcTemplate jdbcTemplate;
    private final TassaStagioneRowMapper rowMapper = new TassaStagioneRowMapper();

    public TassaStagioneDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TassaStagione> findByRegolaId(Integer regolaId) {
        log.debug("TassaStagioneDAO.findByRegolaId() - regolaId={}", regolaId);
        String sql = "SELECT id, fk_regola_id, label, start_month, start_day, end_month, end_day, " +
                     "reduction_pct, created_at " +
                     "FROM tassa_stagione WHERE fk_regola_id = ? ORDER BY start_month, start_day";
        return jdbcTemplate.query(sql, rowMapper, regolaId);
    }

    public TassaStagione insert(TassaStagione s) {
        log.debug("TassaStagioneDAO.insert() - regolaId={} label={}", s.getFkRegolaId(), s.getLabel());
        String sql = "INSERT INTO tassa_stagione " +
                     "(fk_regola_id, label, start_month, start_day, end_month, end_day, reduction_pct) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, s.getFkRegolaId());
            ps.setString(2, s.getLabel());
            ps.setInt(3, s.getStartMonth());
            ps.setInt(4, s.getStartDay());
            ps.setInt(5, s.getEndMonth());
            ps.setInt(6, s.getEndDay());
            ps.setInt(7, s.getReductionPct() != null ? s.getReductionPct() : 0);
            return ps;
        }, keyHolder);
        s.setId(keyHolder.getKey().intValue());
        return s;
    }

    public void deleteByRegolaId(Integer regolaId) {
        log.debug("TassaStagioneDAO.deleteByRegolaId() - regolaId={}", regolaId);
        jdbcTemplate.update("DELETE FROM tassa_stagione WHERE fk_regola_id = ?", regolaId);
    }

    public Map<Integer, Integer> countByRegolaIds(Collection<Integer> regolaIds) {
        if (regolaIds == null || regolaIds.isEmpty()) return Collections.emptyMap();
        String in = regolaIds.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT fk_regola_id, COUNT(*) AS cnt FROM tassa_stagione " +
                     "WHERE fk_regola_id IN (" + in + ") GROUP BY fk_regola_id";
        Map<Integer, Integer> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> { map.put(rs.getInt("fk_regola_id"), rs.getInt("cnt")); },
                regolaIds.toArray());
        return map;
    }
}
