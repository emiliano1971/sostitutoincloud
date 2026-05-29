package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.CodiceTributoRowMapper;
import it.gavia.sostitutoincloud.model.CodiceTributo;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class CodiceTributoDAO {

    private final JdbcTemplate jdbcTemplate;
    private final CodiceTributoRowMapper codiceTributoRowMapper = new CodiceTributoRowMapper();

    public CodiceTributoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CodiceTributo> findAll() {
        log.debug("CodiceTributoDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM codice_tributo ORDER BY id";
        List<CodiceTributo> result = jdbcTemplate.query(sql, codiceTributoRowMapper);
        log.debug("CodiceTributoDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<CodiceTributo> findById(Integer id) {
        log.debug("CodiceTributoDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM codice_tributo WHERE id = ?";
        List<CodiceTributo> result = jdbcTemplate.query(sql, codiceTributoRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<CodiceTributo> findByCodice(String codice) {
        log.debug("CodiceTributoDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM codice_tributo WHERE codice = ?";
        List<CodiceTributo> result = jdbcTemplate.query(sql, codiceTributoRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<CodiceTributo> findByAttivo(Boolean attivo) {
        log.debug("CodiceTributoDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM codice_tributo WHERE attivo = ? ORDER BY id";
        List<CodiceTributo> result = jdbcTemplate.query(sql, codiceTributoRowMapper, attivo);
        log.debug("CodiceTributoDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
