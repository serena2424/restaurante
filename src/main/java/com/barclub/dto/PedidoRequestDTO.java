package com.barclub.dto;

import com.barclub.entity.TipoPedido;
import com.barclub.entity.MetodoPago;
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

    // Opcional: cliente registrado
    private Long clienteId;

    // Obligatorio: id del usuario (cajero/empleado que lo registra)
    @NotNull(message = "El usuario es obligatorio")
    private Long usuarioId;

    // Para pedidos sin cuenta o delivery
    private String nombreCliente;
    private String telefonoCliente;
    @Size(max = 200, message = "La dirección no puede superar los 200 caracteres")
    private String direccionEntrega;
    // Sin @JsonFormat acá a propósito: es lo que MANDA el cliente, y el
    // frontend público envía la hora sin segundos ("21:00"), no "21:00:00".
    // El formato estricto solo hace falta en las respuestas (para que
    // siempre salga igual), nunca en lo que se recibe — si no, Jackson
    // rechaza cualquier hora que no tenga los segundos exactos, con un
    // error 500 al confirmar el pedido (esto pasó de verdad, bug real).
    private LocalTime horarioEntrega;

    // Nº de mesa (pedidos en salón)
    private String mesa;

    // Método de pago elegido por el cliente (opcional)
    private MetodoPago metodoPagoPreferido;

    @NotEmpty(message = "El pedido debe tener al menos un producto")
    @Valid
    private List<DetallePedidoRequestDTO> detalles;
}
