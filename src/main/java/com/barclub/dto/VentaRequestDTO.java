package com.barclub.dto;

import com.barclub.entity.MetodoPago;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VentaRequestDTO {

    @NotNull(message = "El pedido es obligatorio")
    private Long pedidoId;

    @NotNull(message = "El método de pago es obligatorio")
    private MetodoPago metodoPago;
}
