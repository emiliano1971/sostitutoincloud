package it.gavia.sostitutoincloud.config;

import it.gavia.sostitutoincloud.dto.importing.BookingImportRowDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// TODO: aggiungere TTL (es. 30 minuti) per pulizia automatica delle sessioni scadute
@Component
public class ImportSessionCache {

    private final ConcurrentHashMap<String, List<BookingImportRowDTO>> cache = new ConcurrentHashMap<>();
    // Sessioni dei file raw caricati (import V2): byte del file + nome originale.
    private final ConcurrentHashMap<String, RawFile> rawFiles = new ConcurrentHashMap<>();
    // Riga di intestazione (0-based) scelta all'upload, per sessione file raw.
    private final ConcurrentHashMap<String, Integer> headerRows = new ConcurrentHashMap<>();

    public void store(String sessionId, List<BookingImportRowDTO> rows) {
        cache.put(sessionId, rows);
    }

    public List<BookingImportRowDTO> get(String sessionId) {
        return cache.get(sessionId);
    }

    public void remove(String sessionId) {
        cache.remove(sessionId);
    }

    // ── file raw (import V2) ───────────────────────────────────────────────

    public void storeRawFile(String sessionId, byte[] content, String fileName) {
        rawFiles.put(sessionId, new RawFile(content, fileName));
    }

    /** @return contenuto del file, oppure null se non trovato/scaduto. */
    public byte[] getRawFile(String sessionId) {
        RawFile rf = rawFiles.get(sessionId);
        return rf != null ? rf.content() : null;
    }

    public String getFileName(String sessionId) {
        RawFile rf = rawFiles.get(sessionId);
        return rf != null ? rf.fileName() : null;
    }

    public void removeRawFile(String sessionId) {
        rawFiles.remove(sessionId);
        headerRows.remove(sessionId);
    }

    public void storeHeaderRow(String sessionId, int headerRow) {
        headerRows.put(sessionId, headerRow);
    }

    /** @return riga intestazione salvata, oppure 0 se non trovata. */
    public int getHeaderRow(String sessionId) {
        return headerRows.getOrDefault(sessionId, 0);
    }

    private record RawFile(byte[] content, String fileName) {}
}
