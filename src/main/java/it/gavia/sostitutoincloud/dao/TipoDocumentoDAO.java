package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TipoDocumentoRowMapper;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class TipoDocumentoDAO {

    private final JdbcTemplate jdbcTemplate;
    private final TipoDocumentoRowMapper tipoDocumentoRowMapper = new TipoDocumentoRowMapper();

    public TipoDocumentoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TipoDocumento> findAll() {
        log.debug("TipoDocumentoDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, richiede_iva, trasmesso_sdi, attivo, created_at, updated_at " +
                     "FROM tipo_documento ORDER BY id";
        List<TipoDocumento> result = jdbcTemplate.query(sql, tipoDocumentoRowMapper);
        log.debug("TipoDocumentoDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<TipoDocumento> findById(Integer id) {
        log.debug("TipoDocumentoDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, richiede_iva, trasmesso_sdi, attivo, created_at, updated_at " +
                     "FROM tipo_documento WHERE id = ?";
        List<TipoDocumento> result = jdbcTemplate.query(sql, tipoDocumentoRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<TipoDocumento> findByCodice(String codice) {
        log.debug("TipoDocumentoDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, richiede_iva, trasmesso_sdi, attivo, created_at, updated_at " +
                     "FROM tipo_documento WHERE codice = ?";
        List<TipoDocumento> result = jdbcTemplate.query(sql, tipoDocumentoRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<TipoDocumento> findByAttivo(Boolean attivo) {
        log.debug("TipoDocumentoDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, descrizione, richiede_iva, trasmesso_sdi, attivo, created_at, updated_at " +
                     "FROM tipo_documento WHERE attivo = ? ORDER BY id";
        List<TipoDocumento> result = jdbcTemplate.query(sql, tipoDocumentoRowMapper, attivo);
        log.debug("TipoDocumentoDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
