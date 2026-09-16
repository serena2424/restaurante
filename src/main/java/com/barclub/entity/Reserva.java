package com.barclub.entity;

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

    @NotNull(message = "La fecha es obligatoria")
    // columnDefinition="DATE" fuerza que la columna sea siempre una fecha
    // pura (sin componente de hora ni zona horaria) — sin esto, según cómo
    // haya quedado creada la columna, un LocalDate puede terminar
    // guardándose/leyéndose a través de una conversión de zona horaria (el
    // backend tiene Jackson configurado en America/Argentina/Buenos_Aires)
    // y correrse un día para atrás. Esto es justo lo reportado en QA.
    @Column(nullable = false, columnDefinition = "DATE")
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    @Column(nullable = false)
    private LocalTime hora;

    @Min(value = 1, message = "Debe ser al menos 1 persona")
    @Max(value = 20, message = "Máximo 20 personas")
    private Integer cantidadPersonas;

    private String telefono;

    private String aclaraciones;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EstadoReserva estado = EstadoReserva.CONFIRMADA;

    // Cliente registrado (opcional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Cliente cliente;

    public enum EstadoReserva {
        CONFIRMADA, CANCELADA, COMPLETADA
    }
}
