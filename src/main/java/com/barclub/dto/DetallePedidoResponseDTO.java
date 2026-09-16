package com.barclub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetallePedidoResponseDTO {
    private Long id;
    private Integer cantidad;
    private Double precioUnitario;
    private Double subtotal;
    private String variante;
    private ProductoResponseDTO producto;
}
