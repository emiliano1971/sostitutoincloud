package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TipoImmobileRowMapper;
import it.gavia.sostitutoincloud.model.TipoImmobile;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class TipoImmobileDAO {

    private final JdbcTemplate jdbcTemplate;
    private final TipoImmobileRowMapper tipoImmobileRowMapper = new TipoImmobileRowMapper();

    public TipoImmobileDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TipoImmobile> findAll() {
        log.debug("TipoImmobileDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM tipo_immobile ORDER BY id";
        List<TipoImmobile> result = jdbcTemplate.query(sql, tipoImmobileRowMapper);
        log.debug("TipoImmobileDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<TipoImmobile> findById(Integer id) {
        log.debug("TipoImmobileDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM tipo_immobile WHERE id = ?";
        List<TipoImmobile> result = jdbcTemplate.query(sql, tipoImmobileRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<TipoImmobile> findByCodice(String codice) {
        log.debug("TipoImmobileDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM tipo_immobile WHERE codice = ?";
        List<TipoImmobile> result = jdbcTemplate.query(sql, tipoImmobileRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<TipoImmobile> findByAttivo(Boolean attivo) {
        log.debug("TipoImmobileDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, descrizione, attivo, created_at, updated_at " +
                     "FROM tipo_immobile WHERE attivo = ? ORDER BY id";
        List<TipoImmobile> result = jdbcTemplate.query(sql, tipoImmobileRowMapper, attivo);
        log.debug("TipoImmobileDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
