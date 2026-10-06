package com.gestorgastronomico.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Corrección de datos del cliente de un pedido. Solo se cambian los campos que vienen con valor. */
@Data
public class PedidoDatosClienteDTO {

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombreCliente;

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    private String telefonoCliente;

    @Size(max = 200, message = "La dirección no puede superar los 200 caracteres")
    private String direccionEntrega;

    @Size(max = 20, message = "La mesa no puede superar los 20 caracteres")
    private String mesa;
}
