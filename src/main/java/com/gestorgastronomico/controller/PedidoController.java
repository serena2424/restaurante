package com.gestorgastronomico.controller;

import com.gestorgastronomico.dto.DetallePedidoRequestDTO;
import com.gestorgastronomico.dto.PedidoDatosClienteDTO;
import com.gestorgastronomico.dto.PedidoRequestDTO;
import com.gestorgastronomico.dto.PedidoResponseDTO;
import com.gestorgastronomico.entity.EstadoPedido;
import com.gestorgastronomico.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Pedidos de mesa, retiro y delivery")
public class PedidoController {

    private final PedidoService pedidoService;

    @GetMapping
    @Operation(summary = "Todos los pedidos (el mozo recibe solo los suyos)")
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(pedidoService.listarTodos());
    }

    @GetMapping("/entregados-desde-cierre")
    @Operation(summary = "Pedidos entregados en la caja actual")
    public ResponseEntity<List<PedidoResponseDTO>> entregadosDesdeCierre() {
        return ResponseEntity.ok(pedidoService.entregadosDesdeCierre());
    }

    @GetMapping("/activos")
    @Operation(summary = "Pedidos pendientes, en preparación o listos")
    public ResponseEntity<List<PedidoResponseDTO>> listarActivos() {
        return ResponseEntity.ok(pedidoService.listarActivos());
    }

    @GetMapping("/estado/{estado}")
    @Operation(summary = "Pedidos en un estado (cocina solo ve los cancelados de las últimas 24 h)")
    public ResponseEntity<List<PedidoResponseDTO>> listarPorEstado(@PathVariable EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.listarPorEstado(estado));
    }

    @GetMapping("/fecha")
    @Operation(summary = "Pedidos de una fecha (AAAA-MM-DD)")
    public ResponseEntity<List<PedidoResponseDTO>> listarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(pedidoService.listarPorFecha(fecha));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Un pedido")
    public ResponseEntity<PedidoResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear pedido", description = "Desde la web (sin sesión) o desde el panel. Los precios se calculan en el servidor.")
    public ResponseEntity<PedidoResponseDTO> crear(@Valid @RequestBody PedidoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crear(dto));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Avanzar el pedido",
               description = "PENDIENTE → PREPARACION → LISTO. LISTO puede volver a PREPARACION. ENTREGADO solo si ya está cobrado.")
    public ResponseEntity<PedidoResponseDTO> cambiarEstado(@PathVariable Long id, @RequestParam EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.cambiarEstado(id, estado));
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar un pedido que todavía no se cobró")
    public ResponseEntity<PedidoResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.cancelar(id));
    }

    @PostMapping("/{id}/detalles")
    @Operation(summary = "Agregar un producto a un pedido pendiente o en preparación")
    public ResponseEntity<PedidoResponseDTO> agregarDetalle(@PathVariable Long id,
                                                            @Valid @RequestBody DetallePedidoRequestDTO detalleDTO) {
        return ResponseEntity.ok(pedidoService.agregarDetalle(id, detalleDTO));
    }

    @DeleteMapping("/{pedidoId}/detalles/{detalleId}")
    @Operation(summary = "Quitar un producto de un pedido pendiente o en preparación")
    public ResponseEntity<PedidoResponseDTO> eliminarDetalle(@PathVariable Long pedidoId, @PathVariable Long detalleId) {
        return ResponseEntity.ok(pedidoService.eliminarDetalle(pedidoId, detalleId));
    }

    @PatchMapping("/{pedidoId}/detalles/{detalleId}/cantidad")
    @Operation(summary = "Cambiar la cantidad de un producto (0 lo quita)")
    public ResponseEntity<PedidoResponseDTO> cambiarCantidadDetalle(@PathVariable Long pedidoId,
                                                                    @PathVariable Long detalleId,
                                                                    @RequestParam int cantidad) {
        return ResponseEntity.ok(pedidoService.cambiarCantidadDetalle(pedidoId, detalleId, cantidad));
    }

    @PatchMapping("/{pedidoId}/detalles/{detalleId}/estado")
    @Operation(summary = "Cambiar el estado de un plato de una comanda de mesa",
               description = "Cocina: PENDIENTE → PREPARACION → LISTO. Mozo: LISTO → ENTREGADO (bebidas: PENDIENTE → ENTREGADO).")
    public ResponseEntity<PedidoResponseDTO> cambiarEstadoTarjeta(@PathVariable Long pedidoId,
                                                                  @PathVariable Long detalleId,
                                                                  @RequestParam EstadoPedido estado) {
        return ResponseEntity.ok(pedidoService.cambiarEstadoTarjeta(pedidoId, detalleId, estado));
    }

    @PatchMapping("/{pedidoId}/detalles/{detalleId}/cancelar")
    @Operation(summary = "Sacar un plato de una comanda de mesa (queda tachado y no suma)")
    public ResponseEntity<PedidoResponseDTO> cancelarTarjeta(@PathVariable Long pedidoId, @PathVariable Long detalleId) {
        return ResponseEntity.ok(pedidoService.cancelarTarjeta(pedidoId, detalleId));
    }

    @PatchMapping("/{id}/datos-cliente")
    @Operation(summary = "Corregir nombre, teléfono, dirección o mesa")
    public ResponseEntity<PedidoResponseDTO> actualizarDatosCliente(@PathVariable Long id,
                                                                    @Valid @RequestBody PedidoDatosClienteDTO dto) {
        return ResponseEntity.ok(pedidoService.actualizarDatosCliente(id, dto));
    }
}
