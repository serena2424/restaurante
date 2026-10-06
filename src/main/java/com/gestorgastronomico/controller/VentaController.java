package com.gestorgastronomico.controller;

import com.gestorgastronomico.dto.SesionCajaDTO;
import com.gestorgastronomico.dto.VentaRequestDTO;
import com.gestorgastronomico.dto.VentaResponseDTO;
import com.gestorgastronomico.entity.MetodoPago;
import com.gestorgastronomico.service.VentaService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@Tag(name = "Ventas", description = "Cobros, caja e informes")
public class VentaController {

    private final VentaService ventaService;

    @GetMapping
    @Operation(summary = "Todas las ventas")
    public ResponseEntity<List<VentaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(ventaService.listarTodas());
    }

    @GetMapping("/hoy")
    @Operation(summary = "Ventas de la jornada en curso")
    public ResponseEntity<List<VentaResponseDTO>> listarDeHoy() {
        return ResponseEntity.ok(ventaService.listarPorFecha(ventaService.jornadaActual()));
    }

    @GetMapping("/fecha")
    @Operation(summary = "Ventas de una jornada (AAAA-MM-DD)")
    public ResponseEntity<List<VentaResponseDTO>> listarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(ventaService.listarPorFecha(fecha));
    }

    @GetMapping("/total")
    @Operation(summary = "Total de una jornada; sin fecha, la jornada en curso")
    public ResponseEntity<Double> totalDelDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(ventaService.totalDelDia(fecha != null ? fecha : ventaService.jornadaActual()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Una venta")
    public ResponseEntity<VentaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Cobrar un pedido",
               description = "Con entregar = true (por defecto) el pedido pasa a ENTREGADO; con false queda pagado y sigue en cocina.")
    public ResponseEntity<VentaResponseDTO> registrar(@Valid @RequestBody VentaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.registrar(dto));
    }

    @PatchMapping("/{id}/metodo-pago")
    @Operation(summary = "Corregir el medio de pago de un cobro",
               description = "El cajero solo puede corregir cobros de la caja abierta.")
    public ResponseEntity<VentaResponseDTO> corregirMetodoPago(@PathVariable Long id,
                                                               @RequestParam MetodoPago metodoPago) {
        return ResponseEntity.ok(ventaService.corregirMetodoPago(id, metodoPago));
    }

    @DeleteMapping("/hoy")
    @Operation(summary = "Anular todas las ventas de la jornada en curso (solo admin)")
    public ResponseEntity<Void> eliminarHoy() {
        ventaService.eliminarPorFecha(ventaService.jornadaActual());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Anular un cobro (solo admin)")
    public ResponseEntity<Void> eliminarPorId(@PathVariable Long id) {
        ventaService.eliminarPorId(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/desde-cierre")
    @Operation(summary = "Ventas de la caja actual")
    public ResponseEntity<List<VentaResponseDTO>> listarDesdeCierre() {
        return ResponseEntity.ok(ventaService.listarDesdeCierre());
    }

    @GetMapping("/total-desde-cierre")
    @Operation(summary = "Total de la caja actual")
    public ResponseEntity<Double> totalDesdeCierre() {
        return ResponseEntity.ok(ventaService.totalDesdeCierre());
    }

    @GetMapping("/sesiones-hoy")
    @Operation(summary = "Cajas de una jornada con su total; sin fecha, la jornada en curso")
    public ResponseEntity<List<SesionCajaDTO>> sesionesDeHoy(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(ventaService.sesionesDe(fecha != null ? fecha : ventaService.jornadaActual()));
    }

    @GetMapping("/informe")
    @Operation(summary = "Informe de un período: totales, envíos, medios de pago y ranking")
    public ResponseEntity<Map<String, Object>> informe(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(ventaService.informe(desde, hasta));
    }

    @GetMapping("/caja")
    @Operation(summary = "Estado de la caja")
    public ResponseEntity<Map<String, Object>> estadoCaja() {
        return ResponseEntity.ok(ventaService.estadoCaja());
    }

    @PostMapping("/cerrar-caja")
    @Operation(summary = "Cerrar la caja")
    public ResponseEntity<Map<String, Object>> cerrarCaja() {
        return ResponseEntity.ok(ventaService.cerrarCaja());
    }

    @PostMapping("/abrir-caja")
    @Operation(summary = "Abrir una caja nueva")
    public ResponseEntity<Void> abrirCaja() {
        ventaService.abrirCaja();
        return ResponseEntity.noContent().build();
    }
}
