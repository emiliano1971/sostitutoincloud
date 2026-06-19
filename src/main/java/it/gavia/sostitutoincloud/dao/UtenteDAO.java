package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.UtenteRowMapper;
import it.gavia.sostitutoincloud.model.Utente;
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
public class UtenteDAO {

    private static final String SELECT_COLS =
            "SELECT id, fk_tenant_id, email, first_name, last_name, ruolo, attivo, " +
            "last_login, created_at, updated_at, fk_owner_id FROM utente";

    private final JdbcTemplate jdbcTemplate;
    private final UtenteRowMapper utenteRowMapper = new UtenteRowMapper();

    public UtenteDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Utente> findAll() {
        log.debug("UtenteDAO.findAll()");
        List<Utente> result = jdbcTemplate.query(SELECT_COLS + " ORDER BY id", utenteRowMapper);
        log.debug("UtenteDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<Utente> findById(Integer id) {
        log.debug("UtenteDAO.findById() - id={}", id);
        List<Utente> result = jdbcTemplate.query(SELECT_COLS + " WHERE id = ?", utenteRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<Utente> findByEmail(String email) {
        log.debug("UtenteDAO.findByEmail() - email={}", email);
        List<Utente> result = jdbcTemplate.query(SELECT_COLS + " WHERE email = ?", utenteRowMapper, email);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<Utente> findByTenantId(Integer tenantId) {
        log.debug("UtenteDAO.findByTenantId() - tenantId={}", tenantId);
        List<Utente> result = jdbcTemplate.query(
                SELECT_COLS + " WHERE fk_tenant_id = ? AND ruolo != 'super_admin' ORDER BY created_at DESC",
                utenteRowMapper, tenantId);
        log.debug("UtenteDAO.findByTenantId() - trovati {} record", result.size());
        return result;
    }

    public List<Utente> findByRuolo(String ruolo) {
        log.debug("UtenteDAO.findByRuolo() - ruolo={}", ruolo);
        List<Utente> result = jdbcTemplate.query(SELECT_COLS + " WHERE ruolo = ? ORDER BY id", utenteRowMapper, ruolo);
        log.debug("UtenteDAO.findByRuolo() - trovati {} record", result.size());
        return result;
    }

    public List<Utente> findByTenantIdAndRuolo(Integer tenantId, String ruolo) {
        log.debug("UtenteDAO.findByTenantIdAndRuolo() - tenantId={}, ruolo={}", tenantId, ruolo);
        List<Utente> result = jdbcTemplate.query(
                SELECT_COLS + " WHERE fk_tenant_id = ? AND ruolo = ? ORDER BY id",
                utenteRowMapper, tenantId, ruolo);
        log.debug("UtenteDAO.findByTenantIdAndRuolo() - trovati {} record", result.size());
        return result;
    }

    public boolean existsByEmail(String email) {
        log.debug("UtenteDAO.existsByEmail() - email={}", email);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM utente WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    public Utente insert(Utente utente) {
        String sql = "INSERT INTO utente " +
                     "(fk_tenant_id, email, first_name, last_name, password_hash, ruolo, attivo, fk_owner_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, utente.getFkTenantId());
            ps.setString(2, utente.getEmail());
            ps.setString(3, utente.getFirstName());
            ps.setString(4, utente.getLastName());
            ps.setString(5, utente.getPasswordHash());
            ps.setObject(6, utente.getRuolo(), Types.OTHER);
            ps.setBoolean(7, Boolean.TRUE.equals(utente.getAttivo()));
            ps.setObject(8, utente.getFkOwnerId());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("UtenteDAO.insert() - email={} ruolo={}", utente.getEmail(), utente.getRuolo());
        return findById(id).orElseThrow();
    }

    public Utente updateStatus(Integer id, Boolean attivo) {
        log.info("UtenteDAO.updateStatus() - id={} attivo={}", id, attivo);
        jdbcTemplate.update("UPDATE utente SET attivo = ?, updated_at = NOW() WHERE id = ?", attivo, id);
        return findById(id).orElseThrow();
    }

    public void delete(Integer id) {
        log.info("UtenteDAO.delete() - id={}", id);
        jdbcTemplate.update("DELETE FROM utente WHERE id = ?", id);
    }
}
