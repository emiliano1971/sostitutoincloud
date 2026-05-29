package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.RegolaTassaSoggiornoRowMapper;
import it.gavia.sostitutoincloud.model.RegolaTassaSoggiorno;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class RegolaTassaSoggiornoDAO {

    private final JdbcTemplate jdbcTemplate;
    private final RegolaTassaSoggiornoRowMapper regolaTassaSoggiornoRowMapper = new RegolaTassaSoggiornoRowMapper();

    public RegolaTassaSoggiornoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RegolaTassaSoggiorno> findAll() {
        log.debug("RegolaTassaSoggiornoDAO.findAll()");
        String sql = "SELECT id, comune, provincia, importo_per_notte, max_notti, eta_esenzione, " +
                     "valida_dal, valida_al, attivo, created_at, updated_at " +
                     "FROM regola_tassa_soggiorno ORDER BY id";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper);
        log.debug("RegolaTassaSoggiornoDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<RegolaTassaSoggiorno> findById(Integer id) {
        log.debug("RegolaTassaSoggiornoDAO.findById() - id={}", id);
        String sql = "SELECT id, comune, provincia, importo_per_notte, max_notti, eta_esenzione, " +
                     "valida_dal, valida_al, attivo, created_at, updated_at " +
                     "FROM regola_tassa_soggiorno WHERE id = ?";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<RegolaTassaSoggiorno> findByComune(String comune) {
        log.debug("RegolaTassaSoggiornoDAO.findByComune() - comune={}", comune);
        String sql = "SELECT id, comune, provincia, importo_per_notte, max_notti, eta_esenzione, " +
                     "valida_dal, valida_al, attivo, created_at, updated_at " +
                     "FROM regola_tassa_soggiorno WHERE comune = ? ORDER BY id";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper, comune);
        log.debug("RegolaTassaSoggiornoDAO.findByComune() - trovati {} record", result.size());
        return result;
    }

    public List<RegolaTassaSoggiorno> findAttive() {
        log.debug("RegolaTassaSoggiornoDAO.findAttive()");
        String sql = "SELECT id, comune, provincia, importo_per_notte, max_notti, eta_esenzione, " +
                     "valida_dal, valida_al, attivo, created_at, updated_at " +
                     "FROM regola_tassa_soggiorno " +
                     "WHERE attivo = true AND (valida_al IS NULL OR valida_al >= CURRENT_DATE) " +
                     "ORDER BY id";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper);
        log.debug("RegolaTassaSoggiornoDAO.findAttive() - trovati {} record", result.size());
        return result;
    }
}
