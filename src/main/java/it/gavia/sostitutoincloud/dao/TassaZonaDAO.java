package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TassaZonaRowMapper;
import it.gavia.sostitutoincloud.model.TassaZona;
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
public class TassaZonaDAO {

    private final JdbcTemplate jdbcTemplate;
    private final TassaZonaRowMapper rowMapper = new TassaZonaRowMapper();

    public TassaZonaDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TassaZona> findByRegolaId(Integer regolaId) {
        log.debug("TassaZonaDAO.findByRegolaId() - regolaId={}", regolaId);
        String sql = "SELECT id, fk_regola_id, label, reduction_pct, created_at " +
                     "FROM tassa_zona WHERE fk_regola_id = ? ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper, regolaId);
    }

    public TassaZona insert(TassaZona z) {
        log.debug("TassaZonaDAO.insert() - regolaId={} label={}", z.getFkRegolaId(), z.getLabel());
        String sql = "INSERT INTO tassa_zona (fk_regola_id, label, reduction_pct) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, z.getFkRegolaId());
            ps.setString(2, z.getLabel());
            ps.setInt(3, z.getReductionPct() != null ? z.getReductionPct() : 0);
            return ps;
        }, keyHolder);
        z.setId(keyHolder.getKey().intValue());
        return z;
    }

    public void deleteByRegolaId(Integer regolaId) {
        log.debug("TassaZonaDAO.deleteByRegolaId() - regolaId={}", regolaId);
        jdbcTemplate.update("DELETE FROM tassa_zona WHERE fk_regola_id = ?", regolaId);
    }

    public Map<Integer, Integer> countByRegolaIds(Collection<Integer> regolaIds) {
        if (regolaIds == null || regolaIds.isEmpty()) return Collections.emptyMap();
        String in = regolaIds.stream().map(x -> "?").collect(Collectors.joining(","));
        String sql = "SELECT fk_regola_id, COUNT(*) AS cnt FROM tassa_zona " +
                     "WHERE fk_regola_id IN (" + in + ") GROUP BY fk_regola_id";
        Map<Integer, Integer> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> { map.put(rs.getInt("fk_regola_id"), rs.getInt("cnt")); },
                regolaIds.toArray());
        return map;
    }
}
