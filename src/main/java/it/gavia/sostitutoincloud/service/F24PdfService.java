package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CodiceTributoDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dto.pdf.F24PdfDataDTO;
import it.gavia.sostitutoincloud.model.CodiceTributo;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TenantSettings;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Log4j2
public class F24PdfService {

    private static final String TEMPLATE_FILE = "f24-semplificato-acroform.pdf";

    @Value("${app.storage.templates-path:}")
    private String templatesStoragePath;

    private final F24RecordDAO f24RecordDAO;
    private final TenantSettingsDAO tenantSettingsDAO;
    private final TenantDAO tenantDAO;
    private final CodiceTributoDAO codiceTributoDAO;

    public F24PdfService(F24RecordDAO f24RecordDAO,
                         TenantSettingsDAO tenantSettingsDAO,
                         TenantDAO tenantDAO,
                         CodiceTributoDAO codiceTributoDAO) {
        this.f24RecordDAO = f24RecordDAO;
        this.tenantSettingsDAO = tenantSettingsDAO;
        this.tenantDAO = tenantDAO;
        this.codiceTributoDAO = codiceTributoDAO;
    }

    public byte[] generaPdf(Integer tenantId, Integer f24RecordId) {
        // 1. F24 record + verifica tenant
        F24Record f24 = f24RecordDAO.findById(f24RecordId)
                .filter(r -> tenantId.equals(r.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("F24 non trovato: id=" + f24RecordId));

        // 2. Dati anagrafici PM: CF+denominazione da tenant, dati di nascita da tenant_settings.
        Tenant tenant = tenantDAO.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant non trovato: id=" + tenantId));
        TenantSettings settings = tenantSettingsDAO.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Dati anagrafici PM incompleti in tenant_settings"));

        if (blank(tenant.getTaxCode()) || blank(tenant.getLegalName())
                || settings.getDataNascita() == null || blank(settings.getSesso())
                || blank(settings.getComuneNascita()) || blank(settings.getProvinciaNascita())) {
            throw new IllegalStateException("Dati anagrafici PM incompleti in tenant_settings");
        }

        // Codice tributo (stringa) dal lookup
        String codTributo = codiceTributoDAO.findById(f24.getFkCodiceTributoId())
                .map(CodiceTributo::getCodice)
                .orElse("");

        // 3. Costruzione DTO
        BigDecimal totale = f24.getTotalAmount();

        F24PdfDataDTO dto = F24PdfDataDTO.builder()
                .contribuenteCf(tenant.getTaxCode())
                .contribuenteCognomeDenominazione(tenant.getLegalName())
                .contribuenteNome("")
                .contribuenteDataNascitaGg(String.format("%02d", settings.getDataNascita().getDayOfMonth()))
                .contribuenteDataNascitaMm(String.format("%02d", settings.getDataNascita().getMonthValue()))
                .contribuenteDataNascitaAa(String.valueOf(settings.getDataNascita().getYear()))
                .contribuenteSesso(settings.getSesso())
                .contribuenteComuneNascita(settings.getComuneNascita())
                .contribuenteProvincia(settings.getProvinciaNascita())
                .motivoSezione("Erario")
                .motivoCodTributo(codTributo)
                .motivoCodiceEnte("")
                .motivoMeseRif(String.format("%02d", f24.getPeriodoMese()))
                .motivoAnnoRif(String.valueOf(f24.getPeriodoAnno()))
                .motivoImportoDebito(formattaImporto(totale))
                .motivoImportoCredito("")
                .saldoFinaleEuro(formattaEuro(totale))
                .saldoFinaleCent(formattaCent(totale))
                .build();

        // 4. Template: storage esterno con fallback classpath
        byte[] templateBytes;
        try {
            templateBytes = loadTemplate(TEMPLATE_FILE);
        } catch (IOException e) {
            throw new IllegalStateException("Errore lettura template F24", e);
        }

        // 5. Compilazione AcroForm (PDFBox 3.x)
        try (PDDocument doc = Loader.loadPDF(templateBytes)) {
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                throw new IllegalStateException("Il PDF non contiene campi AcroForm");
            }

            Map<String, String> campi = new LinkedHashMap<>();
            campi.put("contribuente_cf", nz(dto.getContribuenteCf()));
            campi.put("contribuente_cognome_denominazione", nz(dto.getContribuenteCognomeDenominazione()));
            campi.put("contribuente_nome", nz(dto.getContribuenteNome()));
            campi.put("contribuente_data_nascita_gg", nz(dto.getContribuenteDataNascitaGg()));
            campi.put("contribuente_data_nascita_mm", nz(dto.getContribuenteDataNascitaMm()));
            campi.put("contribuente_data_nascita_aa", nz(dto.getContribuenteDataNascitaAa()));
            campi.put("contribuente_sesso", nz(dto.getContribuenteSesso()));
            campi.put("contribuente_comune_nascita", nz(dto.getContribuenteComuneNascita()));
            campi.put("contribuente_provincia", nz(dto.getContribuenteProvincia()));
            campi.put("motivo_sezione_1", nz(dto.getMotivoSezione()));
            campi.put("motivo_cod_tributo_1", nz(dto.getMotivoCodTributo()));
            campi.put("motivo_codice_ente", nz(dto.getMotivoCodiceEnte()));
            campi.put("motivo_mese_rif_1", nz(dto.getMotivoMeseRif()));
            campi.put("motivo_anno_rif_1", nz(dto.getMotivoAnnoRif()));
            campi.put("motivo_importo_debito_1", nz(dto.getMotivoImportoDebito()));
            campi.put("motivo_importo_credito_1", nz(dto.getMotivoImportoCredito()));
            campi.put("saldo_finale_euro", nz(dto.getSaldoFinaleEuro()));
            campi.put("saldo_finale_cent", nz(dto.getSaldoFinaleCent()));

            for (Map.Entry<String, String> entry : campi.entrySet()) {
                PDField field = acroForm.getField(entry.getKey());
                if (field != null) {
                    field.setValue(entry.getValue());
                } else {
                    log.warn("Campo AcroForm non trovato: {}", entry.getKey());
                }
            }

            acroForm.flatten();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            byte[] out = baos.toByteArray();
            log.info("F24PdfService.generaPdf() - f24RecordId={} tenantId={} bytes={}",
                    f24RecordId, tenantId, out.length);
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Errore generazione PDF F24: " + e.getMessage(), e);
        }
    }

    // ── formattazione importi ──────────────────────────────────────────────

    /** Importo pieno "104,00"; null o zero → "". */
    private String formattaImporto(BigDecimal val) {
        if (val == null || val.compareTo(BigDecimal.ZERO) == 0) return "";
        return String.format(java.util.Locale.US, "%.2f", val).replace(".", ",");
    }

    /** Solo i centesimi su 2 cifre, es. "00" / "44". null → "00". */
    private String formattaCent(BigDecimal val) {
        if (val == null) return "00";
        int cent = val.remainder(BigDecimal.ONE)
                .multiply(new BigDecimal("100"))
                .abs()
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return String.format("%02d", cent);
    }

    /** Solo la parte intera (euro), es. "104". null → "0". */
    private String formattaEuro(BigDecimal val) {
        if (val == null) return "0";
        return String.valueOf(val.toBigInteger());
    }

    /**
     * Carica il template PDF: prima da storage esterno (aggiornabile senza rebuild),
     * poi fallback dal classpath incluso nel WAR.
     */
    private byte[] loadTemplate(String filename) throws IOException {
        // 1. Storage esterno
        if (templatesStoragePath != null && !templatesStoragePath.isBlank()) {
            File external = new File(templatesStoragePath + "/" + filename);
            if (external.exists() && external.isFile()) {
                log.info("F24PdfService: template da storage esterno: {}", external.getAbsolutePath());
                return Files.readAllBytes(external.toPath());
            }
        }
        // 2. Fallback classpath (sviluppo locale / primo avvio)
        String classpathPath = "/templates/" + filename;
        try (InputStream is = getClass().getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IllegalStateException("Template F24 non trovato né in storage ("
                        + templatesStoragePath + ") né in classpath (" + classpathPath + ")");
            }
            log.warn("F24PdfService: template da classpath (fallback): {}", classpathPath);
            return is.readAllBytes();
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String nz(String s) {
        return s != null ? s : "";
    }
}
