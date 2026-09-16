package com.barclub.dto;

import com.barclub.entity.MetodoPago;
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
public class VentaResponseDTO {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime hora;
    private Double total;
    private MetodoPago metodoPago;
    private Long pedidoId;
    private String nombreCliente;
    private String tipoPedido;
    private String mesa;
}
