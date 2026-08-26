package it.gavia.sostitutoincloud.util;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Caricamento dei template PDF (modelli AcroForm dell'Agenzia delle Entrate).
 *
 * Prima lo storage esterno configurato in app.storage.templates-path — così un modello
 * aggiornato si sostituisce senza rebuild — poi il fallback sul classpath incluso nel WAR.
 */
@Log4j2
public final class PdfTemplateLoader {

    private PdfTemplateLoader() {
    }

    /**
     * @param templatesPath cartella dei template su storage esterno (può essere null/vuota)
     * @param filename      nome del file, es. "CU_modelloORDINARIO_2026.pdf"
     * @throws IllegalStateException se il template non è né in storage né in classpath
     */
    public static byte[] load(String templatesPath, String filename) throws IOException {
        if (templatesPath != null && !templatesPath.isBlank()) {
            File esterno = new File(templatesPath, filename);
            if (esterno.isFile()) {
                log.info("PdfTemplateLoader: template da storage esterno: {}", esterno.getAbsolutePath());
                return Files.readAllBytes(esterno.toPath());
            }
        }
        String classpathPath = "/templates/" + filename;
        try (InputStream is = PdfTemplateLoader.class.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IllegalStateException("Template " + filename + " non trovato né in storage ("
                        + templatesPath + ") né in classpath (" + classpathPath + ")");
            }
            log.warn("PdfTemplateLoader: template da classpath (fallback): {}", classpathPath);
            return is.readAllBytes();
        }
    }
}
