package com.gestorgastronomico.dto;

import com.gestorgastronomico.entity.TipoPedido;
import com.gestorgastronomico.entity.MetodoPago;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalTime;
import java.util.List;

@Data
public class PedidoRequestDTO {

    @NotNull(message = "El tipo de pedido es obligatorio")
    private TipoPedido tipo;

    private Long clienteId;

    /** Se ignora: el pedido queda a nombre de quien inició sesión. Se acepta por compatibilidad. */
    private Long usuarioId;

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombreCliente;
    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String telefonoCliente;
    @Size(max = 200, message = "La dirección no puede superar los 200 caracteres")
    private String direccionEntrega;
    /** Acepta "21:00" o "21:00:00". */
    private LocalTime horarioEntrega;

    @Size(max = 20, message = "La mesa no puede superar los 20 caracteres")
    private String mesa;

    private MetodoPago metodoPagoPreferido;

    @NotEmpty(message = "El pedido debe tener al menos un producto")
    @Valid
    private List<DetallePedidoRequestDTO> detalles;
}
