package it.gavia.sostitutoincloud.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TenantSettings;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.util.TenantAddressUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Generazione PDF dei documenti fiscali (fattura PM / ricevuta owner) da template HTML
 * con OpenHTMLToPDF. I template usano placeholder {NOME_CAMPO} sostituiti con String.replace()
 * — nessun motore di template.
 *
 * Gli importi replicano la stessa semantica di DocumentGenerationService e dei dialog frontend:
 * i valori dei servizi (OTA, pulizie, provvigione PM) sono LORDI e l'IVA va SCORPORATA.
 */
@Service
@Log4j2
public class DocumentPdfService {

    private static final String TEMPLATE_FATTURA = "fattura-pm.html";
    private static final String TEMPLATE_RICEVUTA = "ricevuta-owner.html";

    // Codici della lookup tipo_documento (lo schema usa 'fattura'/'ricevuta',
    // i termini di dominio sono fattura_pm/ricevuta_owner).
    private static final String CODICE_FATTURA = "fattura";
    private static final String CODICE_RICEVUTA = "ricevuta";
    private static final String TIPO_FATTURA_PM = "fattura_pm";
    private static final String TIPO_RICEVUTA_OWNER = "ricevuta_owner";

    private static final DateTimeFormatter DATE_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CENTO = new BigDecimal("100");

    // Percorso di archiviazione PDF: previsto dalla configurazione per un futuro salvataggio
    // su storage. generaPdf() restituisce i byte al chiamante e NON scrive su disco.
    @Value("${app.storage.pdf-path}")
    private String pdfStoragePath;

    @Value("${app.storage.templates-path:}")
    private String templatesPath;

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final BookingDAO bookingDAO;
    private final TenantSettingsDAO tenantSettingsDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final PropertyDAO propertyDAO;
    // Non previsti dalla specifica ma necessari: il tenant serve per l'intestazione
    // emittente ({TENANT_*}), il tipo documento per scegliere il template.
    private final TenantDAO tenantDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;

    public DocumentPdfService(FiscalDocumentDAO fiscalDocumentDAO,
                              BookingDAO bookingDAO,
                              TenantSettingsDAO tenantSettingsDAO,
                              OwnerProfileDAO ownerProfileDAO,
                              PropertyDAO propertyDAO,
                              TenantDAO tenantDAO,
                              TipoDocumentoDAO tipoDocumentoDAO) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.bookingDAO = bookingDAO;
        this.tenantSettingsDAO = tenantSettingsDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.propertyDAO = propertyDAO;
        this.tenantDAO = tenantDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
    }

    public byte[] generaPdf(Integer tenantId, Integer fiscalDocumentId) {
        // 1. Documento + verifica appartenenza al tenant
        FiscalDocument doc = fiscalDocumentDAO.findById(fiscalDocumentId)
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Documento non trovato: id=" + fiscalDocumentId));

        Tenant tenant = tenantDAO.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant non trovato: id=" + tenantId));

        Booking booking = doc.getFkBookingId() != null
                ? bookingDAO.findById(doc.getFkBookingId()).orElse(null)
                : null;
        if (booking == null) {
            throw new IllegalStateException("Documento senza prenotazione collegata: id=" + fiscalDocumentId);
        }
        Property property = propertyDAO.findById(booking.getFkPropertyId()).orElse(null);
        Integer ownerId = doc.getFkOwnerId() != null
                ? doc.getFkOwnerId()
                : (property != null ? property.getFkOwnerId() : null);
        OwnerProfile owner = ownerId != null ? ownerProfileDAO.findById(ownerId).orElse(null) : null;
        TenantSettings settings = tenantSettingsDAO.findByTenantId(tenantId).orElse(null);

        // 2. Tipo documento → template
        String codiceTipo = tipoDocumentoDAO.findById(doc.getFkTipoDocumentoId())
                .map(TipoDocumento::getCodice)
                .orElseThrow(() -> new IllegalStateException(
                        "Tipo documento non risolvibile per il documento id=" + fiscalDocumentId));
        String tipo;
        String templateFile;
        if (CODICE_RICEVUTA.equals(codiceTipo)) {
            tipo = TIPO_RICEVUTA_OWNER;
            templateFile = TEMPLATE_RICEVUTA;
        } else if (CODICE_FATTURA.equals(codiceTipo)) {
            tipo = TIPO_FATTURA_PM;
            templateFile = TEMPLATE_FATTURA;
        } else {
            throw new IllegalStateException("Tipo documento non gestito per il PDF: " + codiceTipo);
        }

        // 3. Template: storage esterno con fallback classpath (stessa strategia di F24PdfService)
        String html;
        try {
            html = loadTemplate(templateFile);
        } catch (IOException e) {
            throw new IllegalStateException("Errore lettura template " + templateFile, e);
        }

        // 4. Sostituzione placeholder
        Map<String, String> campi = TIPO_RICEVUTA_OWNER.equals(tipo)
                ? campiRicevuta(doc, booking, property, owner, tenant, settings)
                : campiFattura(doc, booking, property, tenant);
        for (Map.Entry<String, String> e : campi.entrySet()) {
            html = html.replace("{" + e.getKey() + "}", e.getValue());
        }

        // 5. Render PDF
        byte[] pdf = renderPdf(html);

        log.info("DocumentPdfService.generaPdf() - docId={} tipo={} bytes={}",
                fiscalDocumentId, tipo, pdf.length);
        return pdf;
    }

    // ────────────────────────────── placeholder ──────────────────────────────

    private Map<String, String> campiFattura(FiscalDocument doc, Booking booking,
                                             Property property, Tenant tenant) {
        // L'aliquota memorizzata sul documento decide lo scorporo:
        // 0 (regime forfettario RF19) → imponibile = lordo, IVA = 0.
        BigDecimal aliquotaIva = nz(doc.getAliquotaIva());

        StringBuilder righe = new StringBuilder();
        righe.append(rigaFattura("Riaddebito commissione OTA", nz(booking.getOtaCommissionAmount()), aliquotaIva));
        righe.append(rigaFattura("Riaddebito pulizia finale", nz(booking.getCleaningAmount()), aliquotaIva));
        righe.append(rigaFattura("Provvigione gestione immobiliare", nz(booking.getPmFeeAmount()), aliquotaIva));

        Map<String, String> c = new LinkedHashMap<>();
        c.put("TENANT_LEGAL_NAME", esc(tenant.getLegalName()));
        c.put("TENANT_VAT_NUMBER", esc(tenant.getVatNumber()));
        c.put("TENANT_TAX_CODE", esc(tenant.getTaxCode()));
        c.put("TENANT_LEGAL_ADDRESS", esc(TenantAddressUtils.indirizzoCompleto(tenant)));
        c.put("TENANT_PEC", esc(tenant.getPec()));
        c.put("DOCUMENT_NUMBER", esc(doc.getDocumentNumber()));
        c.put("ISSUE_DATE", data(doc.getIssueDate()));
        c.put("RECIPIENT_NAME", esc(doc.getRecipientName()));
        c.put("RECIPIENT_TAX_CODE", esc(doc.getRecipientTaxCode()));
        c.put("EXTERNAL_BOOKING_ID", esc(booking.getExternalBookingId()));
        c.put("PROPERTY_NAME", esc(property != null ? property.getDisplayName() : null));
        c.put("PROPERTY_ADDRESS", esc(property != null ? property.getAddress() : null));
        c.put("PROPERTY_CITY", esc(property != null ? property.getCity() : null));
        c.put("CHECKIN_DATE", data(booking.getCheckinDate()));
        c.put("CHECKOUT_DATE", data(booking.getCheckoutDate()));
        c.put("NIGHTS", booking.getNights() != null ? String.valueOf(booking.getNights()) : "—");
        c.put("GUESTS", booking.getGuests() != null ? String.valueOf(booking.getGuests()) : "—");
        c.put("ALIQUOTA_IVA", perc(aliquotaIva));
        c.put("RIGHE_FATTURA", righe.toString());
        c.put("TOTAL_IMPONIBILE", importo(doc.getImponibile()));
        c.put("TOTAL_IVA", importo(doc.getVatAmount()));
        c.put("TOTAL_AMOUNT", importo(doc.getTotalAmount()));
        return c;
    }

    private Map<String, String> campiRicevuta(FiscalDocument doc, Booking booking, Property property,
                                              OwnerProfile owner, Tenant tenant, TenantSettings settings) {
        // Canone: colonna dedicata sul documento; fallback = totale meno bollo addebitato.
        BigDecimal canone = doc.getCanoneLocazione() != null
                ? doc.getCanoneLocazione()
                : nz(doc.getTotalAmount()).subtract(nz(doc.getBolloAmount()));
        BigDecimal ritenuta = nz(doc.getRitenutaAmount());
        BigDecimal bollo = nz(doc.getBolloAmount());
        // Netto = canone - ritenuta. Il bollo NON è scalato: è coerente con SettlementService
        // (net_amount = total_amount - withholding_amount) ed è solo informativo.
        BigDecimal netto = canone.subtract(ritenuta).setScale(2, RoundingMode.HALF_UP);

        StringBuilder righe = new StringBuilder();
        righe.append(rigaRicevuta(
                "Canone di locazione turistica breve"
                        + (booking.getCheckinDate() != null && booking.getCheckoutDate() != null
                        ? " — dal " + data(booking.getCheckinDate()) + " al " + data(booking.getCheckoutDate())
                        : ""),
                canone));
        if (bollo.compareTo(BigDecimal.ZERO) > 0) {
            righe.append(rigaRicevuta("Marca da bollo", bollo));
        }

        Map<String, String> c = new LinkedHashMap<>();
        c.put("OWNER_NAME", esc(nomeOwner(owner)));
        c.put("OWNER_TAX_CODE", esc(owner != null ? owner.getTaxCode() : null));
        c.put("TENANT_LEGAL_NAME", esc(tenant.getLegalName()));
        c.put("TENANT_VAT_NUMBER", esc(tenant.getVatNumber()));
        c.put("TENANT_TAX_CODE", esc(tenant.getTaxCode()));
        c.put("TENANT_LEGAL_ADDRESS", esc(TenantAddressUtils.indirizzoCompleto(tenant)));
        c.put("TENANT_PEC", esc(tenant.getPec()));
        c.put("DOCUMENT_NUMBER", esc(doc.getDocumentNumber()));
        c.put("ISSUE_DATE", data(doc.getIssueDate()));
        c.put("GUEST_NAME", esc(booking.getGuestName()));
        c.put("EXTERNAL_BOOKING_ID", esc(booking.getExternalBookingId()));
        c.put("PROPERTY_NAME", esc(property != null ? property.getDisplayName() : null));
        c.put("PROPERTY_ADDRESS", esc(property != null ? property.getAddress() : null));
        c.put("PROPERTY_CITY", esc(property != null ? property.getCity() : null));
        c.put("CHECKIN_DATE", data(booking.getCheckinDate()));
        c.put("CHECKOUT_DATE", data(booking.getCheckoutDate()));
        c.put("NIGHTS", booking.getNights() != null ? String.valueOf(booking.getNights()) : "—");
        c.put("GUESTS", booking.getGuests() != null ? String.valueOf(booking.getGuests()) : "—");
        c.put("RIGHE_RICEVUTA", righe.toString());
        c.put("ALIQUOTA_RITENUTA", perc(aliquotaRitenuta(booking, property, settings, canone, ritenuta)));
        c.put("CANONE_LORDO", importo(canone));
        c.put("RITENUTA", importo(ritenuta));
        c.put("BOLLO", importo(bollo));
        c.put("NETTO_PAGARE", importo(netto));
        return c;
    }

    /** Riga fattura: il valore passato è LORDO, l'IVA viene scorporata. */
    private String rigaFattura(String descrizione, BigDecimal lordo, BigDecimal aliquotaIva) {
        BigDecimal imponibile;
        BigDecimal iva;
        if (aliquotaIva.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal divisore = BigDecimal.ONE.add(aliquotaIva.divide(CENTO, 4, RoundingMode.HALF_UP));
            imponibile = lordo.divide(divisore, 2, RoundingMode.HALF_UP);
            iva = lordo.subtract(imponibile).setScale(2, RoundingMode.HALF_UP);
        } else {
            imponibile = lordo.setScale(2, RoundingMode.HALF_UP);
            iva = BigDecimal.ZERO.setScale(2);
        }
        return "<tr>"
                + "<td>" + esc(descrizione) + "</td>"
                + "<td class=\"text-right\">" + importo(imponibile) + "</td>"
                + "<td class=\"text-right\">" + importo(iva) + "</td>"
                + "<td class=\"text-right\">" + importo(lordo) + "</td>"
                + "</tr>";
    }

    private String rigaRicevuta(String descrizione, BigDecimal importo) {
        return "<tr>"
                + "<td>" + esc(descrizione) + "</td>"
                + "<td class=\"text-right\">" + importo(importo) + "</td>"
                + "</tr>";
    }

    /**
     * Aliquota ritenuta da mostrare: quella memorizzata sul booking, altrimenti quella dei
     * settings in base a primo/secondo immobile, altrimenti ricavata da ritenuta/canone.
     */
    private BigDecimal aliquotaRitenuta(Booking booking, Property property, TenantSettings settings,
                                        BigDecimal canone, BigDecimal ritenuta) {
        if (booking.getAliquotaRitenuta() != null) {
            return booking.getAliquotaRitenuta();
        }
        if (settings != null) {
            boolean primoImmobile = property != null && Boolean.TRUE.equals(property.getPrimoImmobile());
            BigDecimal rate = primoImmobile
                    ? settings.getWithholdingRatePrimary()
                    : settings.getWithholdingRateSecondary();
            if (rate != null) {
                return rate;
            }
        }
        if (canone.compareTo(BigDecimal.ZERO) > 0) {
            return ritenuta.multiply(CENTO).divide(canone, 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private String nomeOwner(OwnerProfile owner) {
        if (owner == null) {
            return null;
        }
        if (owner.getLegalName() != null && !owner.getLegalName().isBlank()) {
            return owner.getLegalName();
        }
        String nome = ((owner.getFirstName() != null ? owner.getFirstName() : "") + " "
                + (owner.getLastName() != null ? owner.getLastName() : "")).trim();
        return nome.isEmpty() ? null : nome;
    }

    // ────────────────────────────── rendering ──────────────────────────────

    private byte[] renderPdf(String html) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Errore generazione PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Carica il template: prima da templatesPath (storage esterno, modificabile senza redeploy),
     * fallback sul classpath /templates/.
     */
    private String loadTemplate(String fileName) throws IOException {
        if (templatesPath != null && !templatesPath.isBlank()) {
            File esterno = new File(templatesPath, fileName);
            if (esterno.isFile()) {
                log.debug("DocumentPdfService.loadTemplate() - da storage esterno: {}", esterno.getAbsolutePath());
                return Files.readString(esterno.toPath(), StandardCharsets.UTF_8);
            }
        }
        try (InputStream is = getClass().getResourceAsStream("/templates/" + fileName)) {
            if (is == null) {
                throw new IOException("Template non trovato nel classpath: /templates/" + fileName);
            }
            log.debug("DocumentPdfService.loadTemplate() - da classpath: /templates/{}", fileName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ────────────────────────────── formattazione ──────────────────────────────

    /** Importo in formato italiano: 1.234,56 */
    private String importo(BigDecimal v) {
        BigDecimal x = nz(v).setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.ITALY, "%,.2f", x);
    }

    /** Aliquota senza decimali inutili: 22.00 → 22 ; 21.50 → 21,5 */
    private String perc(BigDecimal v) {
        BigDecimal x = nz(v).stripTrailingZeros();
        if (x.scale() < 0) {
            x = x.setScale(0);
        }
        return x.toPlainString().replace('.', ',');
    }

    private String data(LocalDate d) {
        return d != null ? d.format(DATE_IT) : "—";
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Escape dei valori inseriti nell'HTML: il renderer richiede XHTML ben formato. */
    private String esc(String v) {
        if (v == null || v.isBlank()) {
            return "—";
        }
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
