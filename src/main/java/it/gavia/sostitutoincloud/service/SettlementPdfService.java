package it.gavia.sostitutoincloud.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementBookingDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementDetailDTO;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.util.PdfTemplateLoader;
import it.gavia.sostitutoincloud.util.TenantAddressUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Generazione PDF del rendiconto di liquidazione da template HTML con OpenHTMLToPDF,
 * stesso approccio di DocumentPdfService: placeholder {NOME_CAMPO} sostituiti con
 * String.replace(), nessun motore di template.
 *
 * Semantica degli importi identica a SettlementDetail.tsx: netto = canone - ritenuta,
 * il bollo è solo informativo e NON viene scalato dal netto pagato al proprietario.
 */
@Service
@Log4j2
public class SettlementPdfService {

    private static final String TEMPLATE = "rendiconto-liquidazione.html";

    private static final DateTimeFormatter DATE_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CENTO = new BigDecimal("100");

    /** Etichetta dello stato in intestazione. 'pending' non è previsto dalla specifica
     *  del template ma è ammesso da SettlementService.STATI_VALIDI. */
    private static final Map<String, String> STATO_LABEL = Map.of(
            "pending", "In attesa",
            "calculated", "Calcolato",
            "approved", "Approvato",
            "paid", "Pagato"
    );

    @Value("${app.storage.templates-path:}")
    private String templatesPath;

    private final SettlementService settlementService;
    private final TenantDAO tenantDAO;
    private final OwnerProfileDAO ownerProfileDAO;

    // NB: TenantSettingsDAO, previsto dalla specifica, non è iniettato — nessun
    // placeholder del template proviene dai tenant_settings (canone, ritenuta e netto
    // sono già calcolati sul settlement), sarebbe una dipendenza morta.
    public SettlementPdfService(SettlementService settlementService,
                                TenantDAO tenantDAO,
                                OwnerProfileDAO ownerProfileDAO) {
        this.settlementService = settlementService;
        this.tenantDAO = tenantDAO;
        this.ownerProfileDAO = ownerProfileDAO;
    }

    public byte[] generaPdf(Integer tenantId, Integer settlementId) {
        // 1. Liquidazione con dettaglio prenotazioni (findById verifica già il tenant)
        SettlementDetailDTO settlement = settlementService.findById(tenantId, settlementId)
                .orElseThrow(() -> new NoSuchElementException("Liquidazione non trovata: id=" + settlementId));

        // 2. Emittente e destinatario
        Tenant tenant = tenantDAO.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant non trovato: id=" + tenantId));
        OwnerProfile owner = settlement.getFkOwnerId() != null
                ? ownerProfileDAO.findById(settlement.getFkOwnerId()).orElse(null)
                : null;

        // 3. Template: storage esterno con fallback classpath
        String html;
        try {
            html = new String(PdfTemplateLoader.load(templatesPath, TEMPLATE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Errore lettura template " + TEMPLATE, e);
        }

        // 4. Sostituzione placeholder
        String ownerName = nomeOwner(settlement, owner);
        Map<String, String> campi = campi(settlement, tenant, owner, ownerName);
        for (Map.Entry<String, String> e : campi.entrySet()) {
            html = html.replace("{" + e.getKey() + "}", e.getValue());
        }

        // 5. Render PDF
        byte[] pdf = renderPdf(html);

        log.info("SettlementPdfService.generaPdf() - settlementId={} ownerName={} bytes={}",
                settlementId, ownerName, pdf.length);
        return pdf;
    }

    // ────────────────────────────── placeholder ──────────────────────────────

    private Map<String, String> campi(SettlementDetailDTO settlement, Tenant tenant,
                                      OwnerProfile owner, String ownerName) {
        List<SettlementBookingDTO> bookings = settlement.getBookings() != null
                ? settlement.getBookings()
                : List.of();

        BigDecimal totLordo = BigDecimal.ZERO;
        BigDecimal totOta = BigDecimal.ZERO;
        BigDecimal totPulizie = BigDecimal.ZERO;
        BigDecimal totPm = BigDecimal.ZERO;
        BigDecimal totBollo = BigDecimal.ZERO;

        StringBuilder righe = new StringBuilder();
        int i = 1;
        for (SettlementBookingDTO b : bookings) {
            // Il canone (ownerNetAmount) e la ritenuta arrivano dal booking senza null-safe
            // (SettlementService.findById), quindi vanno normalizzati qui.
            BigDecimal canone = nz(b.getOwnerNetAmount());
            BigDecimal ritenuta = nz(b.getWithholdingAmount());
            BigDecimal netto = canone.subtract(ritenuta).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bollo = BigDecimal.valueOf(b.getBolloCents() != null ? b.getBolloCents() : 0)
                    .divide(CENTO, 2, RoundingMode.HALF_UP);

            totLordo = totLordo.add(nz(b.getGrossAmount()));
            totOta = totOta.add(nz(b.getOtaCommissionAmount()));
            totPulizie = totPulizie.add(nz(b.getCleaningAmount()));
            totPm = totPm.add(nz(b.getPmFeeAmount()));
            totBollo = totBollo.add(bollo);

            righe.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td style=\"font-family:monospace; font-size:8px\">")
                    .append(esc(b.getExternalBookingId())).append("</td>")
                    .append("<td>").append(esc(b.getPropertyName())).append("</td>")
                    .append(cella("nowrap", data(b.getCheckinDate())))
                    .append(cella("nowrap", data(b.getCheckoutDate())))
                    .append(cella("text-right", fmt(b.getGrossAmount())))
                    .append(cella("text-right negative", neg(b.getOtaCommissionAmount())))
                    .append(cella("text-right negative", neg(b.getCleaningAmount())))
                    .append(cella("text-right negative", neg(b.getPmFeeAmount())))
                    .append(cella("text-right", fmt(canone)))
                    .append(cella("text-right", fmt(bollo)))
                    .append(cella("text-right negative", neg(ritenuta)))
                    .append(cella("text-right positive", fmt(netto)))
                    .append("</tr>");
        }
        if (bookings.isEmpty()) {
            righe.append("<tr><td class=\"empty\" colspan=\"13\">Nessuna prenotazione collegata</td></tr>");
        }

        String stato = settlement.getStato();
        BigDecimal ritenutaTotale = nz(settlement.getWithholdingAmount());

        Map<String, String> c = new LinkedHashMap<>();
        // Tenant / PM
        c.put("TENANT_LEGAL_NAME", esc(tenant.getLegalName()));
        c.put("TENANT_VAT_NUMBER", esc(tenant.getVatNumber()));
        c.put("TENANT_TAX_CODE", esc(tenant.getTaxCode()));
        c.put("TENANT_ADDRESS", esc(TenantAddressUtils.indirizzoCompleto(tenant)));
        c.put("TENANT_PEC", esc(tenant.getPec()));
        // Proprietario
        c.put("OWNER_NAME", esc(ownerName));
        c.put("OWNER_TAX_CODE", esc(owner != null ? owner.getTaxCode() : null));
        c.put("OWNER_IBAN", esc(owner != null ? owner.getIban() : null));
        // Liquidazione
        c.put("PERIODO", esc(settlement.getPeriod()));
        c.put("STATO_LABEL", esc(STATO_LABEL.getOrDefault(stato, stato)));
        c.put("STATO_CSS", stato != null ? stato : "pending");
        c.put("RIGHE_PRENOTAZIONI", righe.toString());
        // Totali: le voci di costo portano già il segno (vedi commento nel template)
        c.put("TOT_LORDO", fmt(totLordo));
        c.put("TOT_OTA", neg(totOta));
        c.put("TOT_PULIZIE", neg(totPulizie));
        c.put("TOT_PM", neg(totPm));
        c.put("TOT_CANONE", fmt(settlement.getTotalAmount()));
        c.put("TOT_BOLLO", fmt(totBollo));
        c.put("TOT_RITENUTA", neg(ritenutaTotale));
        c.put("TOT_RITENUTA_EURO", ritenutaTotale.compareTo(BigDecimal.ZERO) > 0
                ? "-€ " + fmt(ritenutaTotale)
                : "€ " + fmt(BigDecimal.ZERO));
        c.put("TOT_NETTO", fmt(settlement.getNetAmount()));
        c.put("NETTO_DA_PAGARE", fmt(settlement.getNetAmount()));
        c.put("NUM_PRENOTAZIONI", String.valueOf(bookings.size()));
        c.put("DATA_GENERAZIONE", LocalDate.now().format(DATE_IT));
        return c;
    }

    private String cella(String cssClass, String valore) {
        return "<td class=\"" + cssClass + "\">" + valore + "</td>";
    }

    /** Nome del proprietario: quello già risolto dal service, altrimenti dal profilo. */
    private String nomeOwner(SettlementDetailDTO settlement, OwnerProfile owner) {
        if (settlement.getOwnerName() != null && !settlement.getOwnerName().isBlank()) {
            return settlement.getOwnerName();
        }
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

    // ────────────────────────────── formattazione ──────────────────────────────

    /** Importo in formato italiano: 1.234,56 — come DocumentPdfService.importo(). */
    private String fmt(BigDecimal val) {
        BigDecimal x = nz(val).setScale(2, RoundingMode.HALF_UP);
        return String.format(Locale.ITALY, "%,.2f", x);
    }

    /**
     * Voce di costo: il segno meno solo se l'importo è maggiore di zero, altrimenti
     * "0,00" neutro — come fmtCost() in SettlementDetail.tsx. Senza questo controllo
     * una commissione o una pulizia non addebitata stamperebbe "-0,00".
     */
    private String neg(BigDecimal val) {
        BigDecimal x = nz(val);
        return x.compareTo(BigDecimal.ZERO) > 0 ? "-" + fmt(x) : fmt(BigDecimal.ZERO);
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
