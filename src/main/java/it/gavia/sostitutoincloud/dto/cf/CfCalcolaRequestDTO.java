package it.gavia.sostitutoincloud.dto.cf;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CfCalcolaRequestDTO {
    private String cognome;
    private String nome;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataNascita;
    private String sesso;          // "M" o "F"
    private String comuneNascita;  // nome comune o codice Belfiore
}
