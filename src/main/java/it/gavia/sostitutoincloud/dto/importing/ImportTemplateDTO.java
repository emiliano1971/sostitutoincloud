package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTemplateDTO {

    private Integer id;
    private String nome;
    private String descrizione;
    private Integer headerRow;
    private Map<String, String> bookingMapping;
    private Map<String, String> guestMapping;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
