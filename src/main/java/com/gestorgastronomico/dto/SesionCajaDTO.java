package com.gestorgastronomico.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una caja dentro de la jornada: desde que abrió hasta que cerró, o hasta ahora si sigue abierta. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SesionCajaDTO {
    private LocalDateTime apertura;
    private LocalDateTime cierre;
    private Double total;
    private Integer cantidadVentas;
    private boolean abierta;
}
