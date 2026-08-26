package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.ComuneItalianoDAO;
import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.pdf.CuLocazioneBreviDTO;
import it.gavia.sostitutoincloud.dto.pdf.CuPdfDataDTO;
import it.gavia.sostitutoincloud.model.ComuneItaliano;
import it.gavia.sostitutoincloud.model.CuRecord;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.util.PdfTemplateLoader;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Generazione del PDF della Certificazione Unica compilando l'AcroForm del modello
 * ordinario dell'Agenzia delle Entrate. Stesso approccio di F24PdfService.
 */
@Service
@Log4j2
public class CuPdfService {

    private static final String TEMPLATE_FILE = "CU_modelloORDINARIO_2026.pdf";
    /** Causale CU per i redditi da locazione breve soggetti a ritenuta. */
    private static final String CAUSALE_LOCAZIONI = "B";
    /** Il modello ha quattro blocchi "locazioni brevi": loc_01 … loc_04. */
    private static final int MAX_LOCAZIONI = 4;
    /** Lettere del mese nel codice fiscale, in ordine da gennaio a dicembre. */
    private static final String MESI_CF = "ABCDEHLMPRST";
    /**
     * Pagine del modello che servono alle locazioni brevi (indici 0-based):
     * 2 = dati anagrafici, 13 = lavoro autonomo, 14 = locazioni brevi.
     * Le altre 12 pagine vengono scartate dal PDF prodotto.
     */
    private static final Set<Integer> PAGINE_RILEVANTI = Set.of(2, 13, 14);
    private static final int PAGINA_MASSIMA_RICHIESTA = 14;

    @Value("${app.storage.templates-path:}")
    private String templatesPath;

    private final CuRecordDAO cuRecordDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final TenantDAO tenantDAO;
    private final TenantSettingsDAO tenantSettingsDAO;
    private final PropertyDAO propertyDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    // Non previsto dalla specifica: serve per il codice Belfiore del comune dell'immobile
    // e per il comune di nascita del percipiente ricavato dal codice fiscale.
    private final ComuneItalianoDAO comuneItalianoDAO;

    public CuPdfService(CuRecordDAO cuRecordDAO,
                        OwnerProfileDAO ownerProfileDAO,
                        TenantDAO tenantDAO,
                        TenantSettingsDAO tenantSettingsDAO,
                        PropertyDAO propertyDAO,
                        WithholdingLedgerDAO withholdingLedgerDAO,
                        ComuneItalianoDAO comuneItalianoDAO) {
        this.cuRecordDAO = cuRecordDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.tenantDAO = tenantDAO;
        this.tenantSettingsDAO = tenantSettingsDAO;
        this.propertyDAO = propertyDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.comuneItalianoDAO = comuneItalianoDAO;
    }

    public byte[] generaPdf(Integer tenantId, Integer cuRecordId) {
        // 1. CU + verifica tenant e stato
        CuRecord cu = cuRecordDAO.findById(cuRecordId)
                .filter(c -> tenantId.equals(c.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("CU non trovata: id=" + cuRecordId));
        if ("draft".equals(cu.getStato())) {
            throw new IllegalStateException("CU ancora in bozza");
        }

        // 2. Anagrafiche
        OwnerProfile owner = ownerProfileDAO.findById(cu.getFkOwnerId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Proprietario non trovato: id=" + cu.getFkOwnerId()));
        Tenant tenant = tenantDAO.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant non trovato: id=" + tenantId));
        // I settings non contengono dati usati dal modello CU, ma la loro assenza segnala
        // una configurazione incompleta del sostituto: la registro come warning.
        if (tenantSettingsDAO.findByTenantId(tenantId).isEmpty()) {
            log.warn("CuPdfService.generaPdf() - tenant_settings assenti per tenantId={}", tenantId);
        }

        // 3-4. Immobili del proprietario con canoni/ritenute dell'anno
        List<CuLocazioneBreviDTO> locazioni = buildLocazioni(tenantId, cu);

        // 5. DTO
        CuPdfDataDTO dto = buildDto(cu, owner, tenant, locazioni);

        // 6-7. Template e compilazione AcroForm
        byte[] template;
        try {
            template = PdfTemplateLoader.load(templatesPath, TEMPLATE_FILE);
        } catch (IOException e) {
            throw new IllegalStateException("Errore lettura template CU: " + e.getMessage(), e);
        }

        try (PDDocument doc = Loader.loadPDF(template)) {
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                throw new IllegalStateException("Il modello CU non contiene campi AcroForm");
            }
            for (Map.Entry<String, String> campo : campiAcroForm(dto).entrySet()) {
                // Campi senza valore lasciati vuoti: il modello va stampato in bianco dove non applicabile.
                if (campo.getValue() == null || campo.getValue().isBlank()) {
                    continue;
                }
                PDField field = acroForm.getField(campo.getKey());
                if (field == null) {
                    log.warn("CuPdfService - campo AcroForm non trovato nel modello: {}", campo.getKey());
                    continue;
                }
                field.setValue(campo.getValue());
            }
            // Flatten PRIMA di scartare le pagine: converte i campi in contenuto statico,
            // così non restano widget che puntano a pagine rimosse.
            acroForm.flatten();
            estraiPagineRilevanti(doc);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            byte[] pdf = baos.toByteArray();
            log.info("CuPdfService.generaPdf() - cuId={} ownerId={} pagine={} bytes={}",
                    cuRecordId, cu.getFkOwnerId(), doc.getNumberOfPages(), pdf.length);
            return pdf;
        } catch (IOException e) {
            throw new IllegalStateException("Errore generazione PDF CU: " + e.getMessage(), e);
        }
    }

    /**
     * Riduce il documento alle sole pagine rilevanti, rimuovendo le altre dal page tree.
     *
     * Si rimuove dallo stesso PDDocument invece di copiare le pagine in un documento nuovo:
     * addPage() fra due documenti condivide gli oggetti COS e, chiudendo il documento di
     * origine prima del save, i riferimenti restano invalidi. Le pagine tenute conservano
     * l'ordine originale (3, 14, 15) e si rimuove dall'indice più alto al più basso, così
     * gli indici da rimuovere non slittano.
     */
    private void estraiPagineRilevanti(PDDocument doc) {
        if (doc.getNumberOfPages() <= PAGINA_MASSIMA_RICHIESTA) {
            log.warn("CuPdfService - il modello ha {} pagine: estrazione saltata, PDF completo",
                    doc.getNumberOfPages());
            return;
        }
        for (int i = doc.getNumberOfPages() - 1; i >= 0; i--) {
            if (!PAGINE_RILEVANTI.contains(i)) {
                doc.removePage(i);
            }
        }
    }

    // ────────────────────────────── dati ──────────────────────────────

    private List<CuLocazioneBreviDTO> buildLocazioni(Integer tenantId, CuRecord cu) {
        List<Property> immobili = propertyDAO.findByOwnerAndTenant(tenantId, cu.getFkOwnerId()).stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAttivo()))
                .toList();
        if (immobili.size() > MAX_LOCAZIONI) {
            log.warn("CuPdfService - il proprietario {} ha {} immobili attivi ma il modello ne prevede {}: "
                            + "i restanti non compaiono nel quadro locazioni brevi",
                    cu.getFkOwnerId(), immobili.size(), MAX_LOCAZIONI);
        }

        List<CuLocazioneBreviDTO> out = new ArrayList<>();
        for (Property p : immobili) {
            if (out.size() == MAX_LOCAZIONI) {
                break;
            }
            Map<String, Object> agg = withholdingLedgerDAO.aggregaByOwnerPropertyAndAnno(
                    tenantId, cu.getFkOwnerId(), p.getId(), cu.getTaxYear());
            BigDecimal importo = toBigDecimal(agg.get("importo"));
            BigDecimal ritenuta = toBigDecimal(agg.get("ritenuta"));
            // Immobile senza ritenute nell'anno: non va certificato.
            if (importo.compareTo(BigDecimal.ZERO) == 0 && ritenuta.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            out.add(CuLocazioneBreviDTO.builder()
                    .comune(p.getCity())
                    .codiceComuneBelfiore(belfiore(p.getCity()))
                    .importoCorrespettivo(formattaImporto(importo))
                    .ritenuta(formattaImporto(ritenuta))
                    .codiceCin(p.getCinCode())
                    .build());
        }
        return out;
    }

    private CuPdfDataDTO buildDto(CuRecord cu, OwnerProfile owner, Tenant tenant,
                                  List<CuLocazioneBreviDTO> locazioni) {
        CuPdfDataDTO.CuPdfDataDTOBuilder b = CuPdfDataDTO.builder()
                .sostitutoCf(nz(tenant.getTaxCode()))
                .sostitutoDenominazione(nz(tenant.getLegalName()))
                .sostitutiIndirizzo(nz(tenant.getLegalAddress()))
                .sostitutiComune(nz(tenant.getComune()))
                .sostitutiProvincia(nz(tenant.getProvincia()))
                .sostitutiCap(nz(tenant.getCap()))
                .percipienteCf(nz(owner.getTaxCode()))
                .percipienteCognome(cognomePercipiente(owner))
                .percipientiNome(nz(owner.getFirstName()))
                .lavAutoCausale(CAUSALE_LOCAZIONI)
                .lavAutoAnno(String.valueOf(cu.getTaxYear()))
                .lavAutoAmmontareLordo(formattaImporto(cu.getTotalCompensi()))
                .lavAutoImponibile(formattaImporto(cu.getTotalImponibile()))
                .lavAutoRitenuteAcconto(formattaImporto(cu.getTotalRitenute()))
                .locazioni(locazioni);

        // owner_profile non memorizza data/luogo di nascita: si ricavano dal codice fiscale.
        applicaDatiNascitaDaCf(b, owner.getTaxCode());
        return b.build();
    }

    /**
     * Decodifica del codice fiscale di persona fisica: posizioni 7-8 anno, 9 mese (lettera),
     * 10-11 giorno (+40 per le donne), 12-15 codice Belfiore del comune di nascita.
     * CF di persona giuridica o non decodificabile: campi lasciati vuoti.
     */
    private void applicaDatiNascitaDaCf(CuPdfDataDTO.CuPdfDataDTOBuilder b, String cf) {
        if (cf == null || cf.trim().length() != 16) {
            return;
        }
        String v = cf.trim().toUpperCase(Locale.ITALY);
        try {
            String aa = v.substring(6, 8);
            int mese = MESI_CF.indexOf(v.charAt(8)) + 1;
            int giorno = Integer.parseInt(v.substring(9, 11));
            String sesso = giorno > 40 ? "F" : "M";
            if (giorno > 40) {
                giorno -= 40;
            }
            if (mese < 1 || giorno < 1 || giorno > 31) {
                log.warn("CuPdfService - data di nascita non decodificabile dal CF {}", v);
                return;
            }
            // Il CF porta l'anno su 2 cifre, il modello lo vuole su 4: finestra scorrevole
            // sull'anno corrente (es. nel 2026: "85" → 1985, "10" → 2010).
            int annoCorto = Integer.parseInt(aa);
            int annoCompleto = annoCorto <= (LocalDate.now().getYear() % 100)
                    ? 2000 + annoCorto
                    : 1900 + annoCorto;
            String aa4cifre = String.valueOf(annoCompleto);

            b.percipientiDataNascitaGg(String.format("%02d", giorno))
                    .percipientiDataNascitaMm(String.format("%02d", mese))
                    .percipientiDataNascitaAa(aa4cifre)
                    .percipientiSesso(sesso);

            comuneItalianoDAO.findByBelfiore(v.substring(11, 15)).ifPresent(c ->
                    b.percipientiComuneNascita(c.getNome())
                            .percipientiProvinciaNascita(c.getSiglaProvincia()));
        } catch (RuntimeException e) {
            log.warn("CuPdfService - codice fiscale {} non decodificabile: {}", v, e.getMessage());
        }
    }

    /** Mappa DTO → nomi esatti dei campi AcroForm del modello. */
    private Map<String, String> campiAcroForm(CuPdfDataDTO d) {
        Map<String, String> c = new LinkedHashMap<>();
        // Pagina 3 — sostituto d'imposta
        c.put("sostituto_cf", d.getSostitutoCf());
        c.put("sostituto_denominazione", d.getSostitutoDenominazione());
        c.put("sostituto_indirizzo", d.getSostitutiIndirizzo());
        c.put("sostituto_comune", d.getSostitutiComune());
        c.put("sostituto_provincia", d.getSostitutiProvincia());
        c.put("sostituto_cap", d.getSostitutiCap());
        // sostituto_nome e sostituto_prefisso restano vuoti: il sostituto è una persona
        // giuridica identificata dalla denominazione.

        // Pagina 3 — percipiente
        c.put("percipiente_cf", d.getPercipienteCf());
        c.put("percipiente_cognome", d.getPercipienteCognome());
        c.put("percipiente_nome", d.getPercipientiNome());
        c.put("percipiente_sesso", d.getPercipientiSesso());
        // Il modello ha un unico campo per la data, non tre.
        c.put("percipiente_data_nascita_gg/mm/aa", dataNascita(d));
        // Data nascita su 3 campi distinti:
        c.put("percipiente_data_nascita_gg", d.getPercipientiDataNascitaGg());
        c.put("percipiente_data_nascita_mm", d.getPercipientiDataNascitaMm());
        c.put("percipiente_data_nascita_aa", d.getPercipientiDataNascitaAa());

        c.put("percipiente_comune_nascita", d.getPercipientiComuneNascita());
        c.put("percipiente_provincia_nascita", d.getPercipientiProvinciaNascita());

        // Pagina 14 — lavoro autonomo
        c.put("lav_auto_causale", d.getLavAutoCausale());
        c.put("lav_auto_anno", d.getLavAutoAnno());
        c.put("lav_auto_ammontare_lordo", d.getLavAutoAmmontareLordo());
        c.put("lav_auto_imponibile", d.getLavAutoImponibile());
        c.put("lav_auto_ritenute_acconto", d.getLavAutoRitenuteAcconto());

        // Pagina 15 — locazioni brevi, un blocco per immobile
        List<CuLocazioneBreviDTO> loc = d.getLocazioni() != null ? d.getLocazioni() : List.of();
        for (int i = 0; i < loc.size() && i < MAX_LOCAZIONI; i++) {
            String prefix = String.format("loc_%02d_", i + 1);
            CuLocazioneBreviDTO l = loc.get(i);
            c.put(prefix + "comune", l.getComune());
            c.put(prefix + "codice_comune", l.getCodiceComuneBelfiore());
            c.put(prefix + "importo", l.getImportoCorrespettivo());
            c.put(prefix + "ritenuta", l.getRitenuta());
            c.put(prefix + "codice_cin", l.getCodiceCin());
        }
        return c;
    }

    // ────────────────────────────── helper ──────────────────────────────

    private String dataNascita(CuPdfDataDTO d) {
        if (blank(d.getPercipientiDataNascitaGg()) || blank(d.getPercipientiDataNascitaMm())
                || blank(d.getPercipientiDataNascitaAa())) {
            return "";
        }
        return d.getPercipientiDataNascitaGg() + "/" + d.getPercipientiDataNascitaMm()
                + "/" + d.getPercipientiDataNascitaAa();
    }

    /** Persona fisica: cognome. Persona giuridica: denominazione nello stesso campo. */
    private String cognomePercipiente(OwnerProfile owner) {
        if (!blank(owner.getLastName())) {
            return owner.getLastName();
        }
        return nz(owner.getLegalName());
    }

    private String belfiore(String comune) {
        if (blank(comune)) {
            return "";
        }
        return comuneItalianoDAO.findByNomeEsatto(comune.trim())
                .map(ComuneItaliano::getCodiceBelfiore)
                .orElseGet(() -> {
                    log.warn("CuPdfService - comune '{}' non trovato in comune_italiano: Belfiore vuoto", comune);
                    return "";
                });
    }

    /** Importi come nel modello: due decimali con la virgola. Zero → vuoto. */
    private String formattaImporto(BigDecimal val) {
        if (val == null || val.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return String.format(Locale.US, "%.2f", val).replace(".", ",");
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String nz(String s) {
        return s != null ? s : "";
    }
}
