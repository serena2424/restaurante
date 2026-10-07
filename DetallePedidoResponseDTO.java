package com.gestorgastronomico.dto;

import com.gestorgastronomico.entity.EstadoPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    /** Solo en comandas de mesa: estado de la tarjeta. */
    private EstadoPedido estado;
    /** Solo en comandas de mesa: false en bebidas, que no pasan por cocina. */
    private Boolean vaACocina;
    private LocalDateTime creadoEn;
    private LocalDateTime estadoEn;
    private LocalDateTime modificadoEn;
}
