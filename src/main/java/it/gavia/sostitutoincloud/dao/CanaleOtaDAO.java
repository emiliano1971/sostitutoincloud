package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.CanaleOtaRowMapper;
import it.gavia.sostitutoincloud.model.CanaleOta;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
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
                     "tourist_tax_collection, attivo, created_at, updated_at " +
                     "FROM canale_ota ORDER BY id";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper);
        log.debug("CanaleOtaDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<CanaleOta> findById(Integer id) {
        log.debug("CanaleOtaDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "tourist_tax_collection, attivo, created_at, updated_at " +
                     "FROM canale_ota WHERE id = ?";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<CanaleOta> findByCodice(String codice) {
        log.debug("CanaleOtaDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "tourist_tax_collection, attivo, created_at, updated_at " +
                     "FROM canale_ota WHERE codice = ?";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public CanaleOta insert(CanaleOta canale) {
        String sql = "INSERT INTO canale_ota " +
                     "(codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "tourist_tax_collection, attivo) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, canale.getCodice());
            ps.setString(2, canale.getNome());
            ps.setObject(3, canale.getCommissioneDefaultPct());
            ps.setBoolean(4, Boolean.TRUE.equals(canale.getTassaSoggiornoInclusa()));
            ps.setObject(5, canale.getTouristTaxCollection());
            ps.setBoolean(6, Boolean.TRUE.equals(canale.getAttivo()));
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("CanaleOtaDAO.insert() - codice={}", canale.getCodice());
        return findById(id).orElseThrow();
    }

    public CanaleOta update(CanaleOta canale) {
        log.info("CanaleOtaDAO.update() - id={}", canale.getId());
        jdbcTemplate.update(
                "UPDATE canale_ota SET nome = ?, commissione_default_pct = ?, " +
                "tassa_soggiorno_inclusa = ?, tourist_tax_collection = ?, " +
                "attivo = ?, updated_at = NOW() WHERE id = ?",
                canale.getNome(),
                canale.getCommissioneDefaultPct(),
                canale.getTassaSoggiornoInclusa(),
                canale.getTouristTaxCollection(),
                canale.getAttivo(),
                canale.getId());
        return findById(canale.getId()).orElseThrow();
    }

    public CanaleOta updateStatus(Integer id, Boolean attivo) {
        log.info("CanaleOtaDAO.updateStatus() - id={} attivo={}", id, attivo);
        jdbcTemplate.update(
                "UPDATE canale_ota SET attivo = ?, updated_at = NOW() WHERE id = ?",
                attivo, id);
        return findById(id).orElseThrow();
    }

    public List<CanaleOta> findByAttivo(Boolean attivo) {
        log.debug("CanaleOtaDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, " +
                     "tourist_tax_collection, attivo, created_at, updated_at " +
                     "FROM canale_ota WHERE attivo = ? ORDER BY id";
        List<CanaleOta> result = jdbcTemplate.query(sql, canaleOtaRowMapper, attivo);
        log.debug("CanaleOtaDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
