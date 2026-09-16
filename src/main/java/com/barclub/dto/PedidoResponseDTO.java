package com.barclub.dto;

import com.barclub.entity.EstadoPedido;
import com.barclub.entity.TipoPedido;
import com.barclub.entity.MetodoPago;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoResponseDTO {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime hora;
    private EstadoPedido estado;
    private TipoPedido tipo;
    private Double total;
    private Double costoEnvio;
    private String nombreCliente;
    private String telefonoCliente;
    private String direccionEntrega;
    private String mesa;
    private Long creadoPorUsuarioId;
    private String creadoPorNombre;
    private java.time.LocalDateTime modificadoEn;
    private String detalleSnapshotAntesEdicion;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime horarioEntrega;
    private MetodoPago metodoPagoPreferido;
    private ClienteResponseDTO cliente;
    private UsuarioResponseDTO usuario;
    private List<DetallePedidoResponseDTO> detalles;
    private boolean cancelable;
}
