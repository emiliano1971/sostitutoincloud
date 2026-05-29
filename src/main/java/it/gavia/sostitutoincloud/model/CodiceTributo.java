package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class CodiceTributo {

    private Integer id;
    private String codice;
    private String descrizione;
    private Boolean attivo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
