// ============================================================
// VentaController.java
// ============================================================
package com.barclub.controller;

import com.barclub.dto.VentaRequestDTO;
import com.barclub.dto.VentaResponseDTO;
import com.barclub.exception.ErrorResponse;
import com.barclub.service.VentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@Tag(name = "Ventas", description = "Registro de cobros y consulta de ventas del bar")
public class VentaController {

    private final VentaService ventaService;

    @GetMapping
    @Operation(summary = "Listar todas las ventas")
    @ApiResponse(responseCode = "200", description = "Lista completa de ventas")
    public ResponseEntity<List<VentaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(ventaService.listarTodas());
    }

    @GetMapping("/hoy")
    @Operation(summary = "Ventas de hoy")
    @ApiResponse(responseCode = "200", description = "Ventas del día actual")
    public ResponseEntity<List<VentaResponseDTO>> listarDeHoy() {
        return ResponseEntity.ok(ventaService.listarPorFecha(ventaService.jornadaActual()));
    }

    @GetMapping("/fecha")
    @Operation(summary = "Ventas por fecha", description = "Formato YYYY-MM-DD.")
    @ApiResponse(responseCode = "200", description = "Ventas de la fecha indicada")
    public ResponseEntity<List<VentaResponseDTO>> listarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(ventaService.listarPorFecha(fecha));
    }

    @GetMapping("/total")
    @Operation(summary = "Total de ventas de un día", description = "Si no se especifica fecha, devuelve el total de la jornada actual.")
    @ApiResponse(responseCode = "200", description = "Total en pesos")
    public ResponseEntity<Double> totalDelDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate fechaConsulta = fecha != null ? fecha : ventaService.jornadaActual();
        return ResponseEntity.ok(ventaService.totalDelDia(fechaConsulta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener venta por ID")
    @ApiResponse(responseCode = "200", description = "Venta encontrada")
    @ApiResponse(responseCode = "404", description = "Venta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<VentaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Registrar cobro de pedido", description = "El pedido debe estar en estado LISTO. Al registrar la venta, el pedido pasa a ENTREGADO.")
    @ApiResponse(responseCode = "201", description = "Venta registrada")
    @ApiResponse(responseCode = "400", description = "El pedido no está en estado LISTO o ya tiene venta registrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Pedido no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<VentaResponseDTO> registrar(@Valid @RequestBody VentaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.registrar(dto));
    }

    @DeleteMapping("/hoy")
    @Operation(summary = "Eliminar todas las ventas de la jornada actual")
    @ApiResponse(responseCode = "204", description = "Ventas eliminadas")
    public ResponseEntity<Void> eliminarHoy() {
        ventaService.eliminarPorFecha(ventaService.jornadaActual());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una venta por ID")
    @ApiResponse(responseCode = "204", description = "Venta eliminada")
    @ApiResponse(responseCode = "404", description = "Venta no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> eliminarPorId(@PathVariable Long id) {
        ventaService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/desde-cierre")
    @Operation(summary = "Ventas desde el último cierre de caja")
    @ApiResponse(responseCode = "200", description = "Ventas de la caja actual")
    public ResponseEntity<List<VentaResponseDTO>> listarDesdeCierre() {
        return ResponseEntity.ok(ventaService.listarDesdeCierre());
    }

    @GetMapping("/total-desde-cierre")
    @Operation(summary = "Total vendido desde el último cierre de caja")
    @ApiResponse(responseCode = "200", description = "Total en pesos")
    public ResponseEntity<Double> totalDesdeCierre() {
        return ResponseEntity.ok(ventaService.totalDesdeCierre());
    }

    @GetMapping("/sesiones-hoy")
    @Operation(summary = "Cajas de una jornada, cada una con su total", description = "Incluye todas las cajas cerradas esa jornada más la actualmente abierta (si corresponde). Sin parámetro 'fecha', usa la jornada actual (que puede seguir siendo la de ayer si todavía no cruzó el próximo cierre de caja).")
    @ApiResponse(responseCode = "200", description = "Lista de sesiones de caja de la jornada")
    public ResponseEntity<List<com.barclub.dto.SesionCajaDTO>> sesionesDeHoy(
            @RequestParam(required = false) java.time.LocalDate fecha) {
        return ResponseEntity.ok(ventaService.sesionesDe(fecha != null ? fecha : ventaService.jornadaActual()));
    }

    @GetMapping("/informe")
    @Operation(summary = "Informe de ventas por período",
               description = "Facturación, desglose por método de pago y ranking de productos entre dos fechas.")
    @ApiResponse(responseCode = "200", description = "Informe del período")
    public ResponseEntity<Map<String, Object>> informe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(ventaService.informe(desde, hasta));
    }

    @GetMapping("/caja")
    @Operation(summary = "Estado de la caja", description = "Desde cuándo está abierta la caja actual.")
    @ApiResponse(responseCode = "200", description = "Estado de la caja")
    public ResponseEntity<Map<String, Object>> estadoCaja() {
        return ResponseEntity.ok(ventaService.estadoCaja());
    }

    @PostMapping("/cerrar-caja")
    @Operation(summary = "Cerrar la caja", description = "Los totales del panel arrancan de cero hasta el próximo cierre. El historial no se borra.")
    @ApiResponse(responseCode = "200", description = "Resumen del cierre")
    public ResponseEntity<Map<String, Object>> cerrarCaja() {
        return ResponseEntity.ok(ventaService.cerrarCaja());
    }

    @PostMapping("/abrir-caja")
    @Operation(summary = "Abrir la caja", description = "Habilita de nuevo el cobro de pedidos después de un cierre.")
    @ApiResponse(responseCode = "204", description = "Caja abierta")
    public ResponseEntity<Void> abrirCaja() {
        ventaService.abrirCaja();
        return ResponseEntity.noContent().build();
    }
}
