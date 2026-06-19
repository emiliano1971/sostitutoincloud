package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.RegolaTassaSoggiornoRowMapper;
import it.gavia.sostitutoincloud.model.RegolaTassaSoggiorno;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class RegolaTassaSoggiornoDAO {

    private static final String COLUMNS =
            "id, comune, provincia, importo_per_notte, max_notti, eta_esenzione, " +
            "valida_dal, valida_al, attivo, region, max_amount_per_person, " +
            "exemptions, notes, fk_tenant_id, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final RegolaTassaSoggiornoRowMapper regolaTassaSoggiornoRowMapper = new RegolaTassaSoggiornoRowMapper();

    public RegolaTassaSoggiornoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RegolaTassaSoggiorno> findAll() {
        log.debug("RegolaTassaSoggiornoDAO.findAll()");
        String sql = "SELECT " + COLUMNS + " FROM regola_tassa_soggiorno ORDER BY id";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper);
        log.debug("RegolaTassaSoggiornoDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public List<RegolaTassaSoggiorno> findByTenantId(Integer tenantId) {
        log.debug("RegolaTassaSoggiornoDAO.findByTenantId() - tenantId={}", tenantId);
        String sql = "SELECT " + COLUMNS + " FROM regola_tassa_soggiorno WHERE fk_tenant_id = ? ORDER BY id";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper, tenantId);
        log.debug("RegolaTassaSoggiornoDAO.findByTenantId() - trovati {} record", result.size());
        return result;
    }

    public Optional<RegolaTassaSoggiorno> findById(Integer id) {
        log.debug("RegolaTassaSoggiornoDAO.findById() - id={}", id);
        String sql = "SELECT " + COLUMNS + " FROM regola_tassa_soggiorno WHERE id = ?";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<RegolaTassaSoggiorno> findByComune(String comune) {
        log.debug("RegolaTassaSoggiornoDAO.findByComune() - comune={}", comune);
        String sql = "SELECT " + COLUMNS + " FROM regola_tassa_soggiorno WHERE comune = ? ORDER BY id";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper, comune);
        log.debug("RegolaTassaSoggiornoDAO.findByComune() - trovati {} record", result.size());
        return result;
    }

    public List<RegolaTassaSoggiorno> findAttive() {
        log.debug("RegolaTassaSoggiornoDAO.findAttive()");
        String sql = "SELECT " + COLUMNS + " FROM regola_tassa_soggiorno " +
                     "WHERE attivo = true AND (valida_al IS NULL OR valida_al >= CURRENT_DATE) " +
                     "ORDER BY id";
        List<RegolaTassaSoggiorno> result = jdbcTemplate.query(sql, regolaTassaSoggiornoRowMapper);
        log.debug("RegolaTassaSoggiornoDAO.findAttive() - trovati {} record", result.size());
        return result;
    }

    public RegolaTassaSoggiorno insert(RegolaTassaSoggiorno r) {
        log.info("RegolaTassaSoggiornoDAO.insert() - comune={} tenant={}", r.getComune(), r.getFkTenantId());
        String sql = "INSERT INTO regola_tassa_soggiorno " +
                     "(comune, provincia, importo_per_notte, max_notti, eta_esenzione, " +
                     "valida_dal, valida_al, attivo, region, max_amount_per_person, " +
                     "exemptions, notes, fk_tenant_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, r.getComune());
            ps.setString(2, r.getProvincia());
            ps.setObject(3, r.getImportoPerNotte());
            ps.setObject(4, r.getMaxNotti(), Types.SMALLINT);
            ps.setObject(5, r.getEtaEsenzione(), Types.SMALLINT);
            ps.setObject(6, r.getValidaDal());
            ps.setObject(7, r.getValidaAl());
            ps.setBoolean(8, Boolean.TRUE.equals(r.getAttivo()));
            ps.setString(9, r.getRegion());
            ps.setObject(10, r.getMaxAmountPerPerson());
            ps.setString(11, r.getExemptions());
            ps.setString(12, r.getNotes());
            ps.setObject(13, r.getFkTenantId(), Types.INTEGER);
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        return findById(id).orElseThrow();
    }

    public RegolaTassaSoggiorno update(RegolaTassaSoggiorno r) {
        log.info("RegolaTassaSoggiornoDAO.update() - id={}", r.getId());
        jdbcTemplate.update(
                "UPDATE regola_tassa_soggiorno SET " +
                "comune = ?, provincia = ?, importo_per_notte = ?, max_notti = ?, " +
                "eta_esenzione = ?, valida_dal = ?, valida_al = ?, attivo = ?, " +
                "region = ?, max_amount_per_person = ?, exemptions = ?, " +
                "notes = ?, updated_at = NOW() WHERE id = ?",
                r.getComune(),
                r.getProvincia(),
                r.getImportoPerNotte(),
                r.getMaxNotti(),
                r.getEtaEsenzione(),
                r.getValidaDal(),
                r.getValidaAl(),
                r.getAttivo(),
                r.getRegion(),
                r.getMaxAmountPerPerson(),
                r.getExemptions(),
                r.getNotes(),
                r.getId());
        return findById(r.getId()).orElseThrow();
    }

    public RegolaTassaSoggiorno updateStatus(Integer id, Boolean attivo) {
        log.info("RegolaTassaSoggiornoDAO.updateStatus() - id={} attivo={}", id, attivo);
        jdbcTemplate.update(
                "UPDATE regola_tassa_soggiorno SET attivo = ?, updated_at = NOW() WHERE id = ?",
                attivo, id);
        return findById(id).orElseThrow();
    }
}
