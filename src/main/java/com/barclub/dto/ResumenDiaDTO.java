package com.barclub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenDiaDTO {
    private LocalDate fecha;
    private Double totalVentas;
    private Long cantidadPedidos;
    private Long pedidosActivos;
    private Long pedidosEnPreparacion;
    private Long pedidosEntregados;
    private Long reservasDelDia;
    private Double ticketPromedio;
}
