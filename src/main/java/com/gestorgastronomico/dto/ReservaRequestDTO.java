package com.gestorgastronomico.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReservaRequestDTO {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres")
    private String nombreCliente;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    private LocalTime hora;

    @NotNull(message = "Indicá la cantidad de personas")
    @Min(value = 1, message = "Debe haber al menos 1 persona")
    private Integer cantidadPersonas;

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String telefono;

    @Size(max = 255, message = "Las aclaraciones no pueden superar los 255 caracteres")
    private String aclaraciones;

    private Long clienteId;
}
