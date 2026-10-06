package com.gestorgastronomico.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Historial de cajas cerradas: una fila por cada cierre. */
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

    private LocalDateTime fechaApertura;

    private LocalDateTime fechaCierre;

    private Double totalVentas;

    private Integer cantidadVentas;
}
