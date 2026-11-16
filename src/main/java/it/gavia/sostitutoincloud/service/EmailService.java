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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Lunghezza di fiscal_document.email_inviata_a (VARCHAR(500)). */
    private static final int EMAIL_INVIATA_A_MAX = 500;

    private final JavaMailSender mailSender;
    private final DocumentPdfService documentPdfService;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final BookingService bookingService;
    private final AuditService auditService;

    @Value("${app.mail.from}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender,
                        DocumentPdfService documentPdfService,
                        FiscalDocumentDAO fiscalDocumentDAO,
                        TipoDocumentoDAO tipoDocumentoDAO,
                        StatoDocumentoDAO statoDocumentoDAO,
                        BookingService bookingService,
                        AuditService auditService) {
        this.mailSender = mailSender;
        this.documentPdfService = documentPdfService;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.bookingService = bookingService;
        this.auditService = auditService;
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
     * Invia fattura PM e/o ricevuta owner della prenotazione ai destinatari indicati.
     * Allega solo i documenti con id valorizzato: almeno uno dei due è obbligatorio.
     *
     * @param destinatari uno o più indirizzi separati da ; (anche , o spazio)
     * @param fatturaId   fattura PM da allegare, null = non allegarla
     * @param ricevutaId  ricevuta owner da allegare, null = non allegarla
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
        if (fatturaId == null && ricevutaId == null) {
            throw new IllegalArgumentException("Selezionare almeno un allegato");
        }
        for (String addr : indirizzi) {
            try {
                new InternetAddress(addr, true);
            } catch (AddressException e) {
                throw new IllegalArgumentException("Indirizzo email non valido: " + addr);
            }
        }

        // Solo i documenti richiesti (id valorizzato): validati prima di generare qualsiasi PDF
        FiscalDocument fattura = fatturaId != null
                ? documentoDellaPrenotazione(tenantId, bookingId, fatturaId, CODICE_FATTURA, "Fattura") : null;
        FiscalDocument ricevuta = ricevutaId != null
                ? documentoDellaPrenotazione(tenantId, bookingId, ricevutaId, CODICE_RICEVUTA, "Ricevuta") : null;

        // 1-2. PDF dei documenti (stessa generazione del download), nome file → contenuto
        Map<String, byte[]> allegati = new LinkedHashMap<>();
        if (fattura != null) {
            allegati.put(nomeFile("Fattura", fattura), documentPdfService.generaPdf(tenantId, fatturaId));
        }
        if (ricevuta != null) {
            allegati.put(nomeFile("Ricevuta", ricevuta), documentPdfService.generaPdf(tenantId, ricevutaId));
        }

        // 3. Messaggio multipart: testo semplice + 1 o 2 allegati
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailFrom);
        for (String addr : indirizzi) {
            helper.addTo(addr);
        }
        helper.setSubject(oggetto.trim());
        helper.setText(testo != null ? testo : "", false);
        for (Map.Entry<String, byte[]> a : allegati.entrySet()) {
            helper.addAttachment(a.getKey(), new ByteArrayResource(a.getValue()));
        }

        // 4. Invio
        mailSender.send(message);

        // 5. Log
        String elenco = String.join(";", indirizzi);
        log.info("EmailService.inviaDocumenti() - tenant={} booking={} destinatari={} oggetto={} allegati={}",
                tenantId, bookingId, elenco, oggetto, allegati.keySet());

        // 6. Traccia dell'invio, solo sui documenti allegati. L'email è già partita: un errore qui
        // non deve diventare un fallimento per l'utente, si registra e si prosegue.
        try {
            String destinatariColonna = elenco.length() > EMAIL_INVIATA_A_MAX
                    ? elenco.substring(0, EMAIL_INVIATA_A_MAX - 3) + "..." : elenco;
            if (fattura != null) fiscalDocumentDAO.updateEmailInviata(fattura.getId(), destinatariColonna);
            if (ricevuta != null) fiscalDocumentDAO.updateEmailInviata(ricevuta.getId(), destinatariColonna);
        } catch (Exception e) {
            log.error("EmailService.inviaDocumenti() - email inviata ma marcatura documenti fallita booking={}: {}",
                    bookingId, e.getMessage(), e);
        }
        try {
            List<String> numeri = new java.util.ArrayList<>();
            if (fattura != null) numeri.add(fattura.getDocumentNumber());
            if (ricevuta != null) numeri.add(ricevuta.getDocumentNumber());
            auditService.log("email.documenti", "Booking", bookingId,
                    "Email a " + elenco + " — oggetto \"" + oggetto.trim() + "\" — documenti: " + String.join(", ", numeri));
        } catch (Exception e) {
            log.error("EmailService.inviaDocumenti() - email inviata ma audit fallito booking={}: {}",
                    bookingId, e.getMessage(), e);
        }
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
