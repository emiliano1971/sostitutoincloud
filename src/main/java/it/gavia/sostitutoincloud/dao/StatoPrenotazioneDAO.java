package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.StatoPrenotazioneRowMapper;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class StatoPrenotazioneDAO {

    private final JdbcTemplate jdbcTemplate;
    private final StatoPrenotazioneRowMapper statoPrenotazioneRowMapper = new StatoPrenotazioneRowMapper();

    public StatoPrenotazioneDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StatoPrenotazione> findAll() {
        log.debug("StatoPrenotazioneDAO.findAll()");
        String sql = "SELECT id, codice, descrizione, finale, attivo, created_at, updated_at " +
                     "FROM stato_prenotazione ORDER BY id";
        List<StatoPrenotazione> result = jdbcTemplate.query(sql, statoPrenotazioneRowMapper);
        log.debug("StatoPrenotazioneDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<StatoPrenotazione> findById(Integer id) {
        log.debug("StatoPrenotazioneDAO.findById() - id={}", id);
        String sql = "SELECT id, codice, descrizione, finale, attivo, created_at, updated_at " +
                     "FROM stato_prenotazione WHERE id = ?";
        List<StatoPrenotazione> result = jdbcTemplate.query(sql, statoPrenotazioneRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<StatoPrenotazione> findByCodice(String codice) {
        log.debug("StatoPrenotazioneDAO.findByCodice() - codice={}", codice);
        String sql = "SELECT id, codice, descrizione, finale, attivo, created_at, updated_at " +
                     "FROM stato_prenotazione WHERE codice = ?";
        List<StatoPrenotazione> result = jdbcTemplate.query(sql, statoPrenotazioneRowMapper, codice);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<StatoPrenotazione> findByAttivo(Boolean attivo) {
        log.debug("StatoPrenotazioneDAO.findByAttivo() - attivo={}", attivo);
        String sql = "SELECT id, codice, descrizione, finale, attivo, created_at, updated_at " +
                     "FROM stato_prenotazione WHERE attivo = ? ORDER BY id";
        List<StatoPrenotazione> result = jdbcTemplate.query(sql, statoPrenotazioneRowMapper, attivo);
        log.debug("StatoPrenotazioneDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }
}
