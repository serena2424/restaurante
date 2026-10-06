package com.gestorgastronomico.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "ventas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "DATE")
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    /**
     * Día de trabajo al que pertenece: la fecha en que se abrió la caja. Una caja
     * abierta a las 22:00 sigue contando para esa noche después de medianoche.
     * "fecha" y "hora" guardan el momento real del cobro.
     */
    @Column(columnDefinition = "DATE")
    private LocalDate jornada;

    @Column(nullable = false)
    private Double total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodoPago;

    /** Medio de pago original si el cobro se corrigió después. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MetodoPago metodoPagoOriginal;

    private String corregidoPor;

    private LocalDateTime corregidoEn;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;
}
