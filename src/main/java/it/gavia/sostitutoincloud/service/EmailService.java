package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dto.batch.BatchRisultatoDTO;
import it.gavia.sostitutoincloud.dto.batch.InviaEmailBatchDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.document.FiscalDocumentSummaryDTO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.util.EmailUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Invio via email dei documenti fiscali di una prenotazione (fattura PM + ricevuta owner)
 * come allegati PDF. Stesso JavaMailSender e mittente del reset password (app.mail.from).
 */
@Service
@Log4j2
public class EmailService {

    private static final String CODICE_FATTURA = "fattura";
    private static final String CODICE_RICEVUTA = "ricevuta";
    private static final String STATO_ANNULLATA = "annullata";
    private static final String CODICE_NOTA_CREDITO = "nota_credito";

    private final JavaMailSender mailSender;
    private final DocumentPdfService documentPdfService;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final BookingService bookingService;

    @Value("${app.mail.from}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender,
                        DocumentPdfService documentPdfService,
                        FiscalDocumentDAO fiscalDocumentDAO,
                        TipoDocumentoDAO tipoDocumentoDAO,
                        StatoDocumentoDAO statoDocumentoDAO,
                        BookingService bookingService) {
        this.mailSender = mailSender;
        this.documentPdfService = documentPdfService;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.bookingService = bookingService;
    }

    /**
     * Invio massivo: per ogni prenotazione manda fattura PM + ricevuta owner all'email
     * dell'ospite (booking.guest_email), con oggetto e testo comuni. Scartate le prenotazioni
     * non trovate, senza email ospite, senza fattura attiva (mancante, annullata o stornata da
     * NDC) o senza ricevuta attiva, e quelle il cui invio fallisce. Ognuna è indipendente.
     */
    public BatchRisultatoDTO inviaBatch(Integer tenantId, InviaEmailBatchDTO dto) {
        if (dto.getBookingIds() == null || dto.getBookingIds().isEmpty()) {
            throw new IllegalArgumentException("Nessuna prenotazione selezionata");
        }
        if (dto.getOggetto() == null || dto.getOggetto().isBlank()) {
            throw new IllegalArgumentException("Oggetto obbligatorio");
        }
        BatchRisultatoDTO risultato = new BatchRisultatoDTO();

        for (Integer bookingId : dto.getBookingIds()) {
            BookingDetailDTO booking = bookingService.findById(tenantId, bookingId).orElse(null);
            if (booking == null) {
                risultato.scarta("#" + bookingId, "prenotazione non trovata");
                continue;
            }
            String codice = booking.getExternalBookingId() != null ? booking.getExternalBookingId() : "#" + bookingId;
            if (EmailUtils.elenco(booking.getGuestEmail()).isEmpty()) {
                risultato.scarta(codice, "email ospite non presente");
                continue;
            }
            List<FiscalDocumentSummaryDTO> docs = booking.getDocumenti() != null ? booking.getDocumenti() : List.of();
            FiscalDocumentSummaryDTO fattura = attivo(docs, CODICE_FATTURA);
            boolean fatturaStornata = fattura != null && docs.stream()
                    .anyMatch(d -> CODICE_NOTA_CREDITO.equals(d.getTipoDocumento())
                            && fattura.getId().equals(d.getFkDocumentoCollegatoId())
                            && !STATO_ANNULLATA.equals(d.getStatoDocumento()));
            if (fattura == null || fatturaStornata) {
                risultato.scarta(codice, "fattura non emessa o stornata");
                continue;
            }
            FiscalDocumentSummaryDTO ricevuta = attivo(docs, CODICE_RICEVUTA);
            if (ricevuta == null) {
                risultato.scarta(codice, "ricevuta non emessa o annullata");
                continue;
            }
            try {
                inviaDocumenti(tenantId, bookingId, booking.getGuestEmail(), dto.getOggetto(), dto.getTesto(),
                        fattura.getId(), ricevuta.getId());
                risultato.elaborato();
            } catch (Exception e) {
                log.warn("EmailService.inviaBatch() - booking {}: invio fallito: {}", codice, e.getMessage());
                risultato.scarta(codice, "invio fallito — " + e.getMessage());
            }
        }
        log.info("EmailService.inviaBatch() - tenantId={} richiesti={} inviati={} scartati={}",
                tenantId, dto.getBookingIds().size(), risultato.getElaborati(), risultato.getScartati());
        return risultato;
    }

    private FiscalDocumentSummaryDTO attivo(List<FiscalDocumentSummaryDTO> docs, String codiceTipo) {
        return docs.stream()
                .filter(d -> codiceTipo.equals(d.getTipoDocumento()) && !STATO_ANNULLATA.equals(d.getStatoDocumento()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Invia fattura PM e ricevuta owner della prenotazione ai destinatari indicati.
     *
     * @param destinatari uno o più indirizzi separati da ; (anche , o spazio)
     * @throws IllegalArgumentException dati mancanti/non validi o documenti non coerenti con la prenotazione
     * @throws MessagingException       errore nella composizione del messaggio
     * @throws org.springframework.mail.MailException errore SMTP in invio
     */
    public void inviaDocumenti(Integer tenantId, Integer bookingId, String destinatari, String oggetto,
                               String testo, Integer fatturaId, Integer ricevutaId) throws MessagingException {
        List<String> indirizzi = EmailUtils.elenco(destinatari);
        if (indirizzi.isEmpty()) {
            throw new IllegalArgumentException("Destinatario obbligatorio");
        }
        if (oggetto == null || oggetto.isBlank()) {
            throw new IllegalArgumentException("Oggetto obbligatorio");
        }
        if (fatturaId == null) {
            throw new IllegalArgumentException("Fattura non trovata");
        }
        if (ricevutaId == null) {
            throw new IllegalArgumentException("Ricevuta non trovata");
        }
        for (String addr : indirizzi) {
            try {
                new InternetAddress(addr, true);
            } catch (AddressException e) {
                throw new IllegalArgumentException("Indirizzo email non valido: " + addr);
            }
        }

        FiscalDocument fattura = documentoDellaPrenotazione(tenantId, bookingId, fatturaId, CODICE_FATTURA, "Fattura");
        FiscalDocument ricevuta = documentoDellaPrenotazione(tenantId, bookingId, ricevutaId, CODICE_RICEVUTA, "Ricevuta");

        // 1-2. PDF dei documenti (stessa generazione del download)
        byte[] pdfFattura = documentPdfService.generaPdf(tenantId, fatturaId);
        byte[] pdfRicevuta = documentPdfService.generaPdf(tenantId, ricevutaId);

        // 3. Messaggio multipart: testo semplice + 2 allegati
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailFrom);
        for (String addr : indirizzi) {
            helper.addTo(addr);
        }
        helper.setSubject(oggetto.trim());
        helper.setText(testo != null ? testo : "", false);
        helper.addAttachment(nomeFile("Fattura", fattura), new ByteArrayResource(pdfFattura));
        helper.addAttachment(nomeFile("Ricevuta", ricevuta), new ByteArrayResource(pdfRicevuta));

        // 4. Invio
        mailSender.send(message);

        // 5. Log
        log.info("EmailService.inviaDocumenti() - tenant={} booking={} destinatari={} oggetto={}",
                tenantId, bookingId, String.join(";", indirizzi), oggetto);
    }

    /**
     * Documento del tenant, collegato alla prenotazione, del tipo atteso e non annullato.
     * Qualsiasi incoerenza → IllegalArgumentException "{etichetta} non trovata".
     */
    private FiscalDocument documentoDellaPrenotazione(Integer tenantId, Integer bookingId, Integer docId,
                                                      String codiceTipo, String etichetta) {
        FiscalDocument doc = fiscalDocumentDAO.findById(docId)
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .filter(d -> bookingId.equals(d.getFkBookingId()))
                .orElseThrow(() -> new IllegalArgumentException(etichetta + " non trovata"));
        String tipo = tipoDocumentoDAO.findById(doc.getFkTipoDocumentoId())
                .map(TipoDocumento::getCodice).orElse(null);
        if (!codiceTipo.equals(tipo)) {
            throw new IllegalArgumentException(etichetta + " non trovata");
        }
        String stato = doc.getFkStatoDocumentoId() != null
                ? statoDocumentoDAO.findById(doc.getFkStatoDocumentoId()).map(StatoDocumento::getCodice).orElse(null)
                : null;
        if (STATO_ANNULLATA.equals(stato)) {
            throw new IllegalArgumentException(etichetta + " " + doc.getDocumentNumber() + " annullata: non inviabile");
        }
        return doc;
    }

    /** "Fattura-FT-2026-0001.pdf": caratteri non sicuri nel numero sostituiti da "_". */
    private String nomeFile(String prefisso, FiscalDocument doc) {
        String numero = doc.getDocumentNumber() != null ? doc.getDocumentNumber() : String.valueOf(doc.getId());
        return prefisso + "-" + numero.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }
}
