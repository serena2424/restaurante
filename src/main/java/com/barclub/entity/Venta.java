package com.barclub.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
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

    // A qué turno de trabajo pertenece esta venta, para agrupar y sumar
    // "el día" de la forma en que el local realmente trabaja (una caja
    // abierta a las 22:00 y cerrada a las 3am sigue siendo "esa noche", no
    // dos días distintos). "fecha" arriba sigue siendo el dato real de
    // cuándo pasó (para cualquier auditoría); "jornada" es la fecha de la
    // caja bajo la que se cuenta, siempre la del momento en que ESA caja se
    // abrió. Se calcula al registrar la venta, no cambia después.
    @Column(columnDefinition = "DATE")
    private LocalDate jornada;

    @Column(nullable = false)
    private Double total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodoPago;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;
}
