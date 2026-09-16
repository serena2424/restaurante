package com.barclub.dto;

import com.barclub.entity.Reserva;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaResponseDTO {
    private Long id;
    private String nombreCliente;
    // @JsonFormat fuerza el formato exacto acá, sin depender de la
    // configuración global de Jackson (spring.jackson.date-format +
    // time-zone en application.properties) — esa combinación es un patrón
    // conocido de bug donde, en ciertas versiones, termina afectando incluso
    // a un LocalDate (que en teoría no debería tener zona horaria) y lo
    // corre un día para atrás. Esto es justo lo reportado en QA: la base de
    // datos tiene la fecha correcta, pero el panel la mostraba un día antes.
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime hora;
    private Integer cantidadPersonas;
    private String telefono;
    private String aclaraciones;
    private Reserva.EstadoReserva estado;
    private ClienteResponseDTO cliente;
    private boolean cancelable;
}
