package com.barclub.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Para corregir los datos del cliente de un pedido ya creado (por ejemplo,
 * si tipeó mal el teléfono o la dirección). Cada campo es opcional: solo se
 * actualiza el que venga con un valor no nulo, así que el frontend puede
 * mandar solo lo que cambió sin pisar el resto.
 */
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
