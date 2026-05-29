package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.StatoDocumentoRowMapper;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class StatoDocumentoDAO {

    private final JdbcTemplate jdbcTemplate;
    private final StatoDocumentoRowMapper statoDocumentoRowMapper = new StatoDocumentoRowMapper();

    public StatoDocumentoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StatoDocumento> findAll() {
        log.debug("StatoDocumentoDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, is_error, finale, attivo, created_at, updated_at " +
                     "FROM stato_documento ORDER BY id";
        List<StatoDocumento> result = jdbcTemplate.query(sql, statoDocumentoRowMapper);
        log.debug("StatoDocumentoDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<StatoDocumento> findById(Integer id) {
        log.debug("StatoDocumentoDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, is_error, finale, attivo, created_at, updated_at " +
                     "FROM stato_documento WHERE id = ?";
        List<StatoDocumento> result = jdbcTemplate.query(sql, statoDocumentoRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<StatoDocumento> findByCodice(String codice) {
        log.debug("StatoDocumentoDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, is_error, finale, attivo, created_at, updated_at " +
                     "FROM stato_documento WHERE codice = ?";
        List<StatoDocumento> result = jdbcTemplate.query(sql, statoDocumentoRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<StatoDocumento> findByAttivo(Boolean attivo) {
        log.debug("StatoDocumentoDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, descrizione, is_error, finale, attivo, created_at, updated_at " +
                     "FROM stato_documento WHERE attivo = ? ORDER BY id";
        List<StatoDocumento> result = jdbcTemplate.query(sql, statoDocumentoRowMapper, attivo);
        log.debug("StatoDocumentoDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
