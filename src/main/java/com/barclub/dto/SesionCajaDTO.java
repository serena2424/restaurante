package com.barclub.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un "bloque" de caja dentro del día: desde que abrió hasta que cerró (o
 * hasta ahora, si sigue abierta). Se usa para armar "Movimientos de hoy"
 * agrupado por cada caja, en vez de perder las ventas de cajas ya cerradas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SesionCajaDTO {
    private LocalDateTime apertura;
    private LocalDateTime cierre; // null si es la caja actualmente abierta
    private Double total;
    private Integer cantidadVentas;
    private boolean abierta;
}
