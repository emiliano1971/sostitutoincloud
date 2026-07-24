package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComuneItaliano {

    private Integer id;
    private String nome;
    private String siglaProvincia;
    private String regione;
    private String codiceBelfiore;
}
