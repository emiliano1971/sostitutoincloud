package it.gavia.sostitutoincloud.dao;

import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Progressivo di invio SDI per tenant e anno (tabella sdi_progressivo).
 * L'incremento è atomico: INSERT ... ON CONFLICT DO UPDATE ... RETURNING esegue
 * lock di riga e restituisce il nuovo valore in una sola istruzione, quindi due
 * invii concorrenti non possono ottenere lo stesso progressivo.
 */
@Log4j2
@Repository
public class SdiProgressivoDAO {

    /** Il ProgressivoInvio SDI ammette al massimo 5 caratteri alfanumerici. */
    private static final int LUNGHEZZA_PROGRESSIVO = 5;
    private static final int MAX_PROGRESSIVO = 99999;

    private final JdbcTemplate jdbcTemplate;

    public SdiProgressivoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Incrementa e restituisce il prossimo progressivo per tenant/anno, formattato
     * su 5 cifre con zero-padding (es. "00001").
     */
    public String getNextProgressivo(Integer tenantId, Integer anno) {
        String sql = "INSERT INTO sdi_progressivo (fk_tenant_id, anno, ultimo_valore) " +
                "VALUES (?, ?, 1) " +
                "ON CONFLICT (fk_tenant_id, anno) DO UPDATE " +
                "SET ultimo_valore = sdi_progressivo.ultimo_valore + 1, updated_at = NOW() " +
                "RETURNING ultimo_valore";
        Integer valore = jdbcTemplate.queryForObject(sql, Integer.class, tenantId, anno);
        if (valore == null) {
            throw new IllegalStateException(
                    "Impossibile generare il progressivo SDI per tenantId=" + tenantId + " anno=" + anno);
        }
        if (valore > MAX_PROGRESSIVO) {
            throw new IllegalStateException(
                    "Progressivo SDI esaurito per l'anno " + anno + " (massimo " + MAX_PROGRESSIVO + ")");
        }
        String progressivo = String.format("%0" + LUNGHEZZA_PROGRESSIVO + "d", valore);
        log.info("SdiProgressivoDAO.getNextProgressivo() - tenantId={} anno={} progressivo={}",
                tenantId, anno, progressivo);
        return progressivo;
    }
}
