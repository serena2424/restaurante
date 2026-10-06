package com.gestorgastronomico.dto;

import com.gestorgastronomico.entity.MetodoPago;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VentaRequestDTO {

    @NotNull(message = "El pedido es obligatorio")
    private Long pedidoId;

    @NotNull(message = "El método de pago es obligatorio")
    private MetodoPago metodoPago;

    /** false = cobrar sin entregar (el cliente paga al pedir y el pedido sigue en cocina). */
    private Boolean entregar = true;
}
