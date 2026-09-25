package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingSplitEconomicoDAO;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Voci della fattura PM lette da booking_split_economico: le righe attive con
 * include_in_fattura_pm=true e importo &gt; 0, nell'ordine di visualizzazione.
 *
 * <p>Unica fonte delle righe per dettaglio documento (FiscalDocumentService), PDF
 * (DocumentPdfService) e XML SDI (SdiXmlService), così i tre restano coerenti.
 * Lista vuota = prenotazione senza righe split (creata prima della migration 018):
 * il chiamante ripiega sui campi flat del booking.
 *
 * <p>L'aliquota IVA NON viene dalla riga: la applica il chiamante con quella memorizzata
 * sul documento (0 in regime forfettario RF19), mentre le righe split hanno sempre 22.
 */
@Service
@Log4j2
public class VociFatturaPmService {

    /** Voce di fattura: descrizione e importo LORDO (IVA inclusa, da scorporare). */
    public record VoceFatturaPm(String descrizione, BigDecimal lordo) { }

    private final BookingSplitEconomicoDAO splitEconomicoDAO;

    public VociFatturaPmService(BookingSplitEconomicoDAO splitEconomicoDAO) {
        this.splitEconomicoDAO = splitEconomicoDAO;
    }

    public List<VoceFatturaPm> vociDaSplit(Integer bookingId) {
        if (bookingId == null) return List.of();
        List<VoceFatturaPm> voci = splitEconomicoDAO.findByBookingId(bookingId).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIncludeInFatturaPm()))
                .filter(r -> r.getImporto() != null && r.getImporto().signum() > 0)
                .map(r -> new VoceFatturaPm(r.getDescrizione(), r.getImporto().setScale(2, RoundingMode.HALF_UP)))
                .toList();
        log.debug("VociFatturaPmService.vociDaSplit() - bookingId={} voci={}", bookingId, voci.size());
        return voci;
    }
}
