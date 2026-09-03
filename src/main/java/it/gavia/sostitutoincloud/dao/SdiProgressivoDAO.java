package it.gavia.sostitutoincloud.dao;

import lombok.extern.log4j.Log4j2;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Progressivo di invio SDI (tabella sdi_progressivo).
 *
 * <p>Il contatore è GLOBALE per applicazione: una sola riga, senza tenant né anno.
 * Il ProgressivoInvio è quindi unico su tutta l'installazione — più stretto di
 * quanto l'AdE richieda (unicità per partita IVA mittente), quindi sempre valido.
 *
 * <p>Il progressivo è alfanumerico base-26: 5 caratteri A-Z, incrementati come le
 * cifre di un numero in base 26 ('EAAAA' → 'EAAAB' → ... → 'EAAAZ' → 'EAABA').
 * Il tetto è in {@code valore_massimo}.
 *
 * <p>{@code ultimo_valore_alfa} contiene l'ultimo progressivo EMESSO, quindi il seme
 * della riga è il valore precedente al primo da emettere ({@code DZZZZ} → primo
 * progressivo {@code EAAAA}): la logica di incremento ha così un solo ramo.
 *
 * <p>L'incremento è atomico: {@link #getNextProgressivo} è {@code @Transactional} e
 * legge la riga con {@code SELECT ... FOR UPDATE}, che prende un lock esclusivo
 * valido fino al commit. Un invio concorrente attende il rilascio e riprende dal
 * valore già aggiornato, quindi due invii non possono ottenere lo stesso progressivo.
 *
 * <p>La riga dell'applicazione deve esistere: la crea la migration 016 (ed è nello
 * schema di riferimento), il DAO non la inserisce.
 */
@Log4j2
@Repository
public class SdiProgressivoDAO {

    /** Applicazione che consuma il contatore: la tabella è riusabile per altre. */
    private static final String APPLICAZIONE_SDI = "SDI";

    private final JdbcTemplate jdbcTemplate;

    public SdiProgressivoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Incrementa e restituisce il prossimo progressivo, come stringa di 5 caratteri
     * A-Z (es. "EAAAA").
     *
     * @throws IllegalStateException se manca la riga del contatore o se il progressivo
     *                               successivo supera il tetto
     */
    @Transactional
    public String getNextProgressivo() {
        // 1. Lettura con lock pessimistico sulla riga, tenuto fino al commit.
        String sqlSelect = "SELECT ultimo_valore_alfa, valore_massimo " +
                "FROM sdi_progressivo WHERE applicazione = ? FOR UPDATE";
        Map<String, Object> riga;
        try {
            riga = jdbcTemplate.queryForMap(sqlSelect, APPLICAZIONE_SDI);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException(
                    "Contatore progressivo assente per applicazione '" + APPLICAZIONE_SDI +
                            "': manca la riga in sdi_progressivo (vedi migration 016)", e);
        }

        // CHAR(5) è blank-padded: il trim protegge da valori più corti del previsto.
        String corrente = ((String) riga.get("ultimo_valore_alfa")).trim();
        String massimo = ((String) riga.get("valore_massimo")).trim();

        // 2-3. Incremento base-26 e controllo del tetto.
        String prossimo = incrementaAlfa(corrente);
        if (superaLimite(prossimo, massimo)) {
            throw new IllegalStateException(
                    "Progressivo SDI esaurito — limite " + massimo + " raggiunto");
        }

        // 4. Persistenza del nuovo valore, nella stessa transazione del lock.
        jdbcTemplate.update("UPDATE sdi_progressivo SET ultimo_valore_alfa = ?, updated_at = NOW() " +
                "WHERE applicazione = ?", prossimo, APPLICAZIONE_SDI);

        log.info("SdiProgressivoDAO.getNextProgressivo() → {}", prossimo);
        return prossimo;
    }

    /**
     * Incremento base-26 sull'alfabeto A-Z: l'ultimo carattere avanza e, quando è già
     * 'Z', torna ad 'A' propagando il riporto a sinistra.
     */
    private String incrementaAlfa(String corrente) {
        char[] chars = corrente.toCharArray();
        int i = chars.length - 1;
        while (i >= 0) {
            if (chars[i] < 'Z') {
                chars[i]++;
                return new String(chars);
            }
            chars[i] = 'A';
            i--;
        }
        // overflow — tutti i caratteri erano Z
        throw new IllegalStateException("Progressivo SDI esaurito: limite massimo raggiunto");
    }

    /**
     * Confronto lessicografico: su stringhe uppercase della stessa lunghezza l'ordine
     * lessicografico coincide con l'ordine numerico in base 26.
     */
    private boolean superaLimite(String valore, String massimo) {
        return valore.compareTo(massimo) > 0;
    }
}
