package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTemplate {

    private Integer id;
    private Integer fkTenantId;
    private String nome;
    private String descrizione;
    private Integer headerRow;
    private String bookingMapping;  // JSONB come String
    private String guestMapping;    // JSONB come String
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
