package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TassaFasciaEtaRowMapper;
import it.gavia.sostitutoincloud.model.TassaFasciaEta;
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
public class TassaFasciaEtaDAO {

    private final JdbcTemplate jdbcTemplate;
    private final TassaFasciaEtaRowMapper rowMapper = new TassaFasciaEtaRowMapper();

    public TassaFasciaEtaDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TassaFasciaEta> findByRegolaId(Integer regolaId) {
        log.debug("TassaFasciaEtaDAO.findByRegolaId() - regolaId={}", regolaId);
        String sql = "SELECT id, fk_regola_id, label, min_age, max_age, reduction_pct, created_at " +
                     "FROM tassa_fascia_eta WHERE fk_regola_id = ? ORDER BY min_age";
        return jdbcTemplate.query(sql, rowMapper, regolaId);
    }

    public TassaFasciaEta insert(TassaFasciaEta f) {
        log.debug("TassaFasciaEtaDAO.insert() - regolaId={} label={}", f.getFkRegolaId(), f.getLabel());
        String sql = "INSERT INTO tassa_fascia_eta (fk_regola_id, label, min_age, max_age, reduction_pct) " +
                     "VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, f.getFkRegolaId());
            ps.setString(2, f.getLabel());
            ps.setInt(3, f.getMinAge());
            ps.setInt(4, f.getMaxAge());
            ps.setInt(5, f.getReductionPct() != null ? f.getReductionPct() : 0);
            return ps;
        }, keyHolder);
        f.setId(keyHolder.getKey().intValue());
        return f;
    }

    public void deleteByRegolaId(Integer regolaId) {
        log.debug("TassaFasciaEtaDAO.deleteByRegolaId() - regolaId={}", regolaId);
        jdbcTemplate.update("DELETE FROM tassa_fascia_eta WHERE fk_regola_id = ?", regolaId);
    }

    public Map<Integer, Integer> countByRegolaIds(Collection<Integer> regolaIds) {
        if (regolaIds == null || regolaIds.isEmpty()) return Collections.emptyMap();
        String in = regolaIds.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT fk_regola_id, COUNT(*) AS cnt FROM tassa_fascia_eta " +
                     "WHERE fk_regola_id IN (" + in + ") GROUP BY fk_regola_id";
        Map<Integer, Integer> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> { map.put(rs.getInt("fk_regola_id"), rs.getInt("cnt")); },
                regolaIds.toArray());
        return map;
    }
}
