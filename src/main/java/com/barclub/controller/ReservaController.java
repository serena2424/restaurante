package com.barclub.controller;

import com.barclub.dto.ReservaRequestDTO;
import com.barclub.dto.ReservaResponseDTO;
import com.barclub.exception.ErrorResponse;
import com.barclub.service.ReservaService;
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

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Gestión de reservas de mesas del bar")
public class ReservaController {

    private final ReservaService reservaService;

    @GetMapping
    @Operation(summary = "Listar todas las reservas")
    @ApiResponse(responseCode = "200", description = "Lista completa de reservas")
    public ResponseEntity<List<ReservaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(reservaService.listarTodas());
    }

    @GetMapping("/hoy")
    @Operation(summary = "Listar reservas de hoy", description = "Devuelve reservas confirmadas para el día actual.")
    @ApiResponse(responseCode = "200", description = "Reservas del día")
    public ResponseEntity<List<ReservaResponseDTO>> listarDeHoy() {
        return ResponseEntity.ok(reservaService.listarDeHoy());
    }

    @GetMapping("/proximas")
    @Operation(summary = "Listar reservas próximas", description = "Reservas confirmadas desde hoy hasta N días adelante (por defecto 7). Se usa para avisos y recordatorios, sin importar la fecha de la reserva.")
    @ApiResponse(responseCode = "200", description = "Reservas próximas")
    public ResponseEntity<List<ReservaResponseDTO>> listarProximas(
            @RequestParam(defaultValue = "7") int dias) {
        return ResponseEntity.ok(reservaService.listarProximas(dias));
    }

    @GetMapping("/fecha")
    @Operation(summary = "Listar reservas por fecha", description = "Formato de fecha: YYYY-MM-DD.")
    @ApiResponse(responseCode = "200", description = "Reservas de la fecha indicada")
    public ResponseEntity<List<ReservaResponseDTO>> listarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(reservaService.listarPorFecha(fecha));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener reserva por ID")
    @ApiResponse(responseCode = "200", description = "Reserva encontrada")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ReservaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear reserva", description = "No se permiten fechas pasadas. Máximo 20 personas.")
    @ApiResponse(responseCode = "201", description = "Reserva creada exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o fecha en el pasado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ReservaResponseDTO> crear(@Valid @RequestBody ReservaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar reserva", description = "No se puede modificar una reserva cancelada.")
    @ApiResponse(responseCode = "200", description = "Reserva actualizada")
    @ApiResponse(responseCode = "400", description = "Reserva cancelada o datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ReservaResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ReservaRequestDTO dto) {
        return ResponseEntity.ok(reservaService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar reserva", description = "Solo se puede cancelar hasta 2 horas antes del horario reservado.")
    @ApiResponse(responseCode = "200", description = "Reserva cancelada")
    @ApiResponse(responseCode = "400", description = "No se puede cancelar (ya cancelada, completada, o fuera de tiempo)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.cancelar(id));
    }

    @PatchMapping("/{id}/completar")
    @Operation(summary = "Marcar reserva como completada")
    @ApiResponse(responseCode = "200", description = "Reserva completada")
    @ApiResponse(responseCode = "400", description = "Solo se pueden completar reservas confirmadas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ReservaResponseDTO> completar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.completar(id));
    }
}
