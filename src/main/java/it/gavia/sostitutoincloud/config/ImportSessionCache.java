package it.gavia.sostitutoincloud.config;

import it.gavia.sostitutoincloud.dto.importing.BookingImportRowDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// TODO: aggiungere TTL (es. 30 minuti) per pulizia automatica delle sessioni scadute
@Component
public class ImportSessionCache {

    private final ConcurrentHashMap<String, List<BookingImportRowDTO>> cache = new ConcurrentHashMap<>();

    public void store(String sessionId, List<BookingImportRowDTO> rows) {
        cache.put(sessionId, rows);
    }

    public List<BookingImportRowDTO> get(String sessionId) {
        return cache.get(sessionId);
    }

    public void remove(String sessionId) {
        cache.remove(sessionId);
    }
}
