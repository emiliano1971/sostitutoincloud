package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.ScenarioFiscaleRowMapper;
import it.gavia.sostitutoincloud.model.ScenarioFiscale;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class ScenarioFiscaleDAO {

    private final JdbcTemplate jdbcTemplate;
    private final ScenarioFiscaleRowMapper scenarioFiscaleRowMapper = new ScenarioFiscaleRowMapper();

    public ScenarioFiscaleDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ScenarioFiscale> findAll() {
        log.debug("ScenarioFiscaleDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM scenario_fiscale ORDER BY id";
        List<ScenarioFiscale> result = jdbcTemplate.query(sql, scenarioFiscaleRowMapper);
        log.debug("ScenarioFiscaleDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<ScenarioFiscale> findById(Integer id) {
        log.debug("ScenarioFiscaleDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM scenario_fiscale WHERE id = ?";
        List<ScenarioFiscale> result = jdbcTemplate.query(sql, scenarioFiscaleRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<ScenarioFiscale> findByCodice(String codice) {
        log.debug("ScenarioFiscaleDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM scenario_fiscale WHERE codice = ?";
        List<ScenarioFiscale> result = jdbcTemplate.query(sql, scenarioFiscaleRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<ScenarioFiscale> findByAttivo(Boolean attivo) {
        log.debug("ScenarioFiscaleDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM scenario_fiscale WHERE attivo = ? ORDER BY id";
        List<ScenarioFiscale> result = jdbcTemplate.query(sql, scenarioFiscaleRowMapper, attivo);
        log.debug("ScenarioFiscaleDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
