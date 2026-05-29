package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.SdiEsitoRowMapper;
import it.gavia.sostitutoincloud.model.SdiEsito;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class SdiEsitoDAO {

    private final JdbcTemplate jdbcTemplate;
    private final SdiEsitoRowMapper sdiEsitoRowMapper = new SdiEsitoRowMapper();

    public SdiEsitoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SdiEsito> findAll() {
        log.debug("SdiEsitoDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, created_at, updated_at " +
                     "FROM sdi_esito ORDER BY id";
        List<SdiEsito> result = jdbcTemplate.query(sql, sdiEsitoRowMapper);
        log.debug("SdiEsitoDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<SdiEsito> findById(Integer id) {
        log.debug("SdiEsitoDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, created_at, updated_at " +
                     "FROM sdi_esito WHERE id = ?";
        List<SdiEsito> result = jdbcTemplate.query(sql, sdiEsitoRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<SdiEsito> findByCodice(String codice) {
        log.debug("SdiEsitoDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, created_at, updated_at " +
                     "FROM sdi_esito WHERE codice = ?";
        List<SdiEsito> result = jdbcTemplate.query(sql, sdiEsitoRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
