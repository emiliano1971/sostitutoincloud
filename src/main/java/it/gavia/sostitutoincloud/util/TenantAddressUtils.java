package it.gavia.sostitutoincloud.util;

import it.gavia.sostitutoincloud.model.Tenant;

/**
 * Composizione dell'indirizzo del tenant per la visualizzazione su una riga singola.
 *
 * Dalla migration 011 la sede legale è scomposta: legal_address contiene solo via e
 * civico, mentre cap/comune/provincia sono colonne dedicate (l'XML SDI le usa come
 * elementi separati in Sede). Dove serve l'indirizzo completo — PDF, anteprime a
 * schermo, DTO — va ricomposto con questo helper, così il formato resta uno solo.
 */
public final class TenantAddressUtils {

    private TenantAddressUtils() {
    }

    /**
     * Es. "Via Roma 1, 00100 Roma (RM)". Ogni parte è opzionale: se mancano CAP,
     * comune e provincia il risultato è il solo indirizzo.
     *
     * @return indirizzo completo, oppure null se tenant è null
     */
    public static String indirizzoCompleto(Tenant tenant) {
        if (tenant == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (notBlank(tenant.getLegalAddress())) {
            sb.append(tenant.getLegalAddress().trim());
        }
        if (notBlank(tenant.getCap())) {
            sb.append(sb.length() > 0 ? ", " : "").append(tenant.getCap().trim());
        }
        if (notBlank(tenant.getComune())) {
            sb.append(sb.length() > 0 ? " " : "").append(tenant.getComune().trim());
        }
        if (notBlank(tenant.getProvincia())) {
            sb.append(" (").append(tenant.getProvincia().trim()).append(")");
        }
        return sb.toString();
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
