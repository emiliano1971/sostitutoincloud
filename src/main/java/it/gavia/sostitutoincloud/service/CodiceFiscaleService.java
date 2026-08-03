package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.ComuneItalianoDAO;
import it.gavia.sostitutoincloud.model.ComuneItaliano;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Calcolo del codice fiscale italiano secondo l'algoritmo ufficiale dell'Agenzia delle Entrate.
 */
@Service
@Log4j2
public class CodiceFiscaleService {

    private static final String VOCALI = "AEIOU";
    private static final String MESI = "ABCDEHLMPRST"; // gen..dic
    // Chiavi allineate all'indice della tabella dispari (0-9 poi A-Z).
    private static final String KEYS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int[] ODD = {
            1, 0, 5, 7, 9, 13, 15, 17, 19, 21,               // 0-9
            1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, // A-N
            11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23      // O-Z
    };

    private final ComuneItalianoDAO comuneItalianoDAO;
    private final BookingDAO bookingDAO;

    public CodiceFiscaleService(ComuneItalianoDAO comuneItalianoDAO, BookingDAO bookingDAO) {
        this.comuneItalianoDAO = comuneItalianoDAO;
        this.bookingDAO = bookingDAO;
    }

    /**
     * Genera un CF fittizio per ospite straniero: "EST" + anno(4) + progressivo(9) = 16 caratteri.
     * Il progressivo è il numero di booking del tenant con CF estero nell'anno + 1.
     */
    public String generaCfEstero(Integer tenantId, Integer anno) {
        int count = bookingDAO.countCfEsteroByTenantAndAnno(tenantId, anno);
        String progressivo = String.format("%09d", count + 1);
        String cf = "EST" + anno + progressivo;
        log.info("CodiceFiscaleService.generaCfEstero() - tenantId={} cf={}", tenantId, cf);
        return cf;
    }

    public String calcola(String cognome, String nome, LocalDate dataNascita, String sesso, String comuneNascita) {
        if (cognome == null || cognome.isBlank()) throw new IllegalArgumentException("Cognome mancante");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome mancante");
        if (dataNascita == null) throw new IllegalArgumentException("Data di nascita mancante");
        String sex = sesso == null ? "" : sesso.trim().toUpperCase();
        if (!sex.equals("M") && !sex.equals("F")) throw new IllegalArgumentException("Sesso non valido: " + sesso);
        if (comuneNascita == null || comuneNascita.isBlank()) throw new IllegalArgumentException("Comune di nascita mancante");

        String belfiore = risolviBelfiore(comuneNascita);

        String cognomeCode = codiceCognome(clean(cognome));
        String nomeCode = codiceNome(clean(nome));
        String annoCode = String.format("%02d", dataNascita.getYear() % 100);
        char meseCode = MESI.charAt(dataNascita.getMonthValue() - 1);
        int giorno = dataNascita.getDayOfMonth() + ("F".equals(sex) ? 40 : 0);
        String giornoCode = String.format("%02d", giorno);

        String parziale = (cognomeCode + nomeCode + annoCode + meseCode + giornoCode + belfiore).toUpperCase();
        char controllo = carattereControllo(parziale);
        String cf = parziale + controllo;

        log.info("CodiceFiscaleService.calcola() - nome={} cognome={} nato={} comune={}",
                nome, cognome, dataNascita, comuneNascita);
        return cf;
    }

    public Optional<String> calcolaSafe(String cognome, String nome, LocalDate dataNascita,
                                        String sesso, String comuneNascita) {
        try {
            return Optional.of(calcola(cognome, nome, dataNascita, sesso, comuneNascita));
        } catch (IllegalArgumentException e) {
            log.warn("CodiceFiscaleService.calcolaSafe() - CF non calcolabile: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── componenti ──────────────────────────────────────────────────────────

    /** Cerca prima per nome esatto, poi come codice Belfiore diretto. */
    private String risolviBelfiore(String comuneNascita) {
        String val = comuneNascita.trim();
        Optional<ComuneItaliano> byNome = comuneItalianoDAO.findByNomeEsatto(val);
        if (byNome.isPresent()) return byNome.get().getCodiceBelfiore();
        Optional<ComuneItaliano> byCode = comuneItalianoDAO.findByBelfiore(val.toUpperCase());
        if (byCode.isPresent()) return byCode.get().getCodiceBelfiore();
        throw new IllegalArgumentException("Comune non trovato: " + comuneNascita);
    }

    private String codiceCognome(String s) {
        String cons = onlyConsonants(s);
        String voc = onlyVowels(s);
        return (cons + voc + "XXX").substring(0, 3);
    }

    private String codiceNome(String s) {
        String cons = onlyConsonants(s);
        if (cons.length() >= 4) {
            return "" + cons.charAt(0) + cons.charAt(2) + cons.charAt(3);
        }
        String voc = onlyVowels(s);
        return (cons + voc + "XXX").substring(0, 3);
    }

    private char carattereControllo(String parziale) {
        int sum = 0;
        for (int i = 0; i < parziale.length(); i++) {
            char c = parziale.charAt(i);
            if (i % 2 == 0) { // posizione dispari (1-indexed) → tabella dispari
                sum += ODD[KEYS.indexOf(c)];
            } else {          // posizione pari → tabella pari (A=0..Z=25, 0=0..9=9)
                sum += Character.isDigit(c) ? c - '0' : c - 'A';
            }
        }
        return (char) ('A' + (sum % 26));
    }

    /** Normalizza: rimuove accenti, uppercase, tiene solo A-Z. */
    private String clean(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toUpperCase().replaceAll("[^A-Z]", "");
    }

    private String onlyConsonants(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (VOCALI.indexOf(c) < 0) sb.append(c);
        return sb.toString();
    }

    private String onlyVowels(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) if (VOCALI.indexOf(c) >= 0) sb.append(c);
        return sb.toString();
    }
}
