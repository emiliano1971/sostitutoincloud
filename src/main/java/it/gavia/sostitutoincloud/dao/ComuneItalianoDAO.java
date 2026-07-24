package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.ComuneItalianoRowMapper;
import it.gavia.sostitutoincloud.model.ComuneItaliano;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class ComuneItalianoDAO {

    private static final String SELECT_ALL =
            "SELECT id, nome, sigla_provincia, regione, codice_belfiore FROM comune_italiano";

    private final JdbcTemplate jdbcTemplate;
    private final ComuneItalianoRowMapper rowMapper = new ComuneItalianoRowMapper();

    public ComuneItalianoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Ricerca per prefisso del nome (autocomplete). */
    public List<ComuneItaliano> findByNome(String nome) {
        log.debug("ComuneItalianoDAO.findByNome() - nome={}", nome);
        String sql = SELECT_ALL + " WHERE LOWER(nome) LIKE LOWER(?) || '%' ORDER BY nome LIMIT 20";
        return jdbcTemplate.query(sql, rowMapper, nome);
    }

    public Optional<ComuneItaliano> findByNomeEsatto(String nome) {
        log.debug("ComuneItalianoDAO.findByNomeEsatto() - nome={}", nome);
        String sql = SELECT_ALL + " WHERE LOWER(nome) = LOWER(?) LIMIT 1";
        List<ComuneItaliano> result = jdbcTemplate.query(sql, rowMapper, nome);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<ComuneItaliano> findByBelfiore(String codice) {
        log.debug("ComuneItalianoDAO.findByBelfiore() - codice={}", codice);
        String sql = SELECT_ALL + " WHERE codice_belfiore = ?";
        List<ComuneItaliano> result = jdbcTemplate.query(sql, rowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
