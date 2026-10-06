package com.gestorgastronomico.dto;

import com.gestorgastronomico.entity.Reserva;
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
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime hora;
    private Integer cantidadPersonas;
    private String telefono;
    private String aclaraciones;
    private Reserva.EstadoReserva estado;
    private ClienteResponseDTO cliente;
    private boolean noAsistio;
    private boolean cancelable;
}
