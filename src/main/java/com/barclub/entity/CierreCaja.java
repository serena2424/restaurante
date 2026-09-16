package com.barclub.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro histórico de cada caja cerrada (una fila por cada vez que se
 * presiona "Cerrar caja"). Antes solo se guardaba el momento del último
 * cierre (en ConfigLocal.cierreCaja) y se perdía todo lo anterior — por eso
 * "Movimientos de hoy" mostraba "Sin ventas" recién cerrada la caja, aunque
 * en el día ya se hubiera vendido en una caja previa. Con este historial se
 * puede armar la vista del día completo separada por cada caja.
 */
@Entity
@Table(name = "cierres_caja")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CierreCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Desde cuándo se contaban las ventas de esta caja (el cierre anterior,
    // o el inicio del día si era la primera caja).
    private LocalDateTime fechaApertura;

    // Cuándo se cerró esta caja.
    private LocalDateTime fechaCierre;

    private Double totalVentas;

    private Integer cantidadVentas;
}
