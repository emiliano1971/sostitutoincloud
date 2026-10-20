package it.gavia.sostitutoincloud.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validazione formale di codice fiscale e partita IVA (solo formato, non il carattere di controllo).
 * <ul>
 *   <li>CF persona fisica: 16 caratteri alfanumerici;</li>
 *   <li>P.IVA: 11 cifre (senza prefisso IT). Società e ditte hanno CF numerico = P.IVA.</li>
 * </ul>
 * Le regole per tipo di soggetto (tenant, proprietario) lanciano IllegalArgumentException → 400.
 */
public final class CodiceFiscalePivaValidator {

    private static final Pattern CF = Pattern.compile("^[A-Z0-9]{16}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIVA = Pattern.compile("^\\d{11}$");

    private static final String PERSONA_FISICA = "persona_fisica";
    private static final Set<String> OWNER_TYPES = Set.of(PERSONA_FISICA, "piva", "societa");

    private CodiceFiscalePivaValidator() {
    }

    public static boolean isCF(String v) {
        return v != null && CF.matcher(v.trim()).matches();
    }

    public static boolean isPIVA(String v) {
        return v != null && PIVA.matcher(v.trim()).matches();
    }

    /** CF a 16 caratteri oppure CF numerico a 11 cifre (= P.IVA). */
    public static boolean isValid(String v) {
        return isCF(v) || isPIVA(v);
    }

    /** trim + maiuscolo; null o blank → null. */
    public static String normalizza(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Tenant: P.IVA obbligatoria (11 cifre); CF a 16 caratteri o 11 cifre, se assente = P.IVA.
     * Valori già normalizzati. Restituisce il CF da salvare.
     */
    public static String validaTenant(String taxCode, String vatNumber) {
        if (!isPIVA(vatNumber)) {
            throw new IllegalArgumentException("Partita IVA obbligatoria (11 cifre numeriche)");
        }
        if (taxCode == null) {
            return vatNumber;
        }
        if (!isValid(taxCode)) {
            throw new IllegalArgumentException(
                    "Codice fiscale non valido: inserire 16 caratteri alfanumerici o 11 cifre");
        }
        return taxCode;
    }

    /**
     * Proprietario. Valori già normalizzati. Restituisce il CF da salvare.
     * <ul>
     *   <li>persona_fisica: CF a 16 caratteri obbligatorio; P.IVA facoltativa ma, se presente, a 11 cifre;</li>
     *   <li>piva / societa: P.IVA obbligatoria; CF a 16 caratteri o 11 cifre, se assente = P.IVA.</li>
     * </ul>
     */
    public static String validaOwner(String ownerType, String taxCode, String vatNumber) {
        if (ownerType == null || !OWNER_TYPES.contains(ownerType)) {
            throw new IllegalArgumentException(
                    "Tipo proprietario non valido: " + ownerType + " (ammessi: persona_fisica, piva, societa)");
        }
        if (PERSONA_FISICA.equals(ownerType)) {
            if (!isCF(taxCode)) {
                throw new IllegalArgumentException(
                        "Codice fiscale obbligatorio per persona fisica (16 caratteri alfanumerici)");
            }
            if (vatNumber != null && !isPIVA(vatNumber)) {
                throw new IllegalArgumentException("Partita IVA non valida (11 cifre numeriche)");
            }
            return taxCode;
        }
        if (!isPIVA(vatNumber)) {
            throw new IllegalArgumentException(
                    "Partita IVA obbligatoria per azienda/professionista (11 cifre numeriche)");
        }
        if (taxCode == null) {
            return vatNumber;
        }
        if (!isValid(taxCode)) {
            throw new IllegalArgumentException(
                    "Codice fiscale non valido: inserire 16 caratteri alfanumerici o 11 cifre");
        }
        return taxCode;
    }
}
