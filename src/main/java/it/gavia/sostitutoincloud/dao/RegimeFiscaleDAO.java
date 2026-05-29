package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.RegimeFiscaleRowMapper;
import it.gavia.sostitutoincloud.model.RegimeFiscale;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class RegimeFiscaleDAO {

    private final JdbcTemplate jdbcTemplate;
    private final RegimeFiscaleRowMapper regimeFiscaleRowMapper = new RegimeFiscaleRowMapper();

    public RegimeFiscaleDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RegimeFiscale> findAll() {
        log.debug("RegimeFiscaleDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM regime_fiscale ORDER BY id";
        List<RegimeFiscale> result = jdbcTemplate.query(sql, regimeFiscaleRowMapper);
        log.debug("RegimeFiscaleDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<RegimeFiscale> findById(Integer id) {
        log.debug("RegimeFiscaleDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM regime_fiscale WHERE id = ?";
        List<RegimeFiscale> result = jdbcTemplate.query(sql, regimeFiscaleRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<RegimeFiscale> findByCodice(String codice) {
        log.debug("RegimeFiscaleDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM regime_fiscale WHERE codice = ?";
        List<RegimeFiscale> result = jdbcTemplate.query(sql, regimeFiscaleRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<RegimeFiscale> findByAttivo(Boolean attivo) {
        log.debug("RegimeFiscaleDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM regime_fiscale WHERE attivo = ? ORDER BY id";
        List<RegimeFiscale> result = jdbcTemplate.query(sql, regimeFiscaleRowMapper, attivo);
        log.debug("RegimeFiscaleDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
