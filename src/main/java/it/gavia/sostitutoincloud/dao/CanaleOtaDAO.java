package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.CanaleOtaRowMapper;
import it.gavia.sostitutoincloud.model.CanaleOta;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class CanaleOtaDAO {

    private final JdbcTemplate jdbcTemplate;
    private final CanaleOtaRowMapper canaleOtaRowMapper = new CanaleOtaRowMapper();

    public CanaleOtaDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CanaleOta> findAll() {
        log.debug("CanaleOtaDAO.findAll()");
        String sql = "SELECT id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "attivo, created_at, updated_at " +
                     "FROM canale_ota ORDER BY id";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper);
        log.debug("CanaleOtaDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<CanaleOta> findById(Integer id) {
        log.debug("CanaleOtaDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "attivo, created_at, updated_at " +
                     "FROM canale_ota WHERE id = ?";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<CanaleOta> findByCodice(String codice) {
        log.debug("CanaleOtaDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "attivo, created_at, updated_at " +
                     "FROM canale_ota WHERE codice = ?";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<CanaleOta> findByAttivo(Boolean attivo) {
        log.debug("CanaleOtaDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "attivo, created_at, updated_at " +
                     "FROM canale_ota WHERE attivo = ? ORDER BY id";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper, attivo);
        log.debug("CanaleOtaDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
