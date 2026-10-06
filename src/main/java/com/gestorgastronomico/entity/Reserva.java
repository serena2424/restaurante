package com.gestorgastronomico.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Column(nullable = false)
    private String nombreCliente;

    // DATE puro: sin hora ni zona horaria, para que la fecha no se corra un día.
    @NotNull(message = "La fecha es obligatoria")
    @Column(nullable = false, columnDefinition = "DATE")
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    @Column(nullable = false)
    private LocalTime hora;

    // El máximo depende de la configuración del local (ver ReservaService).
    @Min(value = 1, message = "Debe ser al menos 1 persona")
    private Integer cantidadPersonas;

    private String telefono;

    private String aclaraciones;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EstadoReserva estado = EstadoReserva.CONFIRMADA;

    /** true cuando se canceló porque el cliente no vino. */
    private Boolean noAsistio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Cliente cliente;

    public enum EstadoReserva {
        CONFIRMADA, CANCELADA, COMPLETADA
    }
}
