package com.gestorgastronomico.controller;

import com.gestorgastronomico.dto.ReservaRequestDTO;
import com.gestorgastronomico.dto.ReservaResponseDTO;
import com.gestorgastronomico.service.ReservaService;
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
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Reservas de mesas")
public class ReservaController {

    private final ReservaService reservaService;

    @GetMapping
    @Operation(summary = "Todas las reservas")
    public ResponseEntity<List<ReservaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(reservaService.listarTodas());
    }

    @GetMapping("/hoy")
    @Operation(summary = "Reservas de hoy, incluidas las canceladas, ordenadas por hora")
    public ResponseEntity<List<ReservaResponseDTO>> listarDeHoy() {
        return ResponseEntity.ok(reservaService.listarDeHoy());
    }

    @GetMapping("/proximas")
    @Operation(summary = "Reservas confirmadas desde hoy hasta dentro de N días")
    public ResponseEntity<List<ReservaResponseDTO>> listarProximas(@RequestParam(defaultValue = "7") int dias) {
        return ResponseEntity.ok(reservaService.listarProximas(dias));
    }

    @GetMapping("/fecha")
    @Operation(summary = "Reservas de una fecha (AAAA-MM-DD), ordenadas por hora")
    public ResponseEntity<List<ReservaResponseDTO>> listarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(reservaService.listarPorFecha(fecha));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Una reserva")
    public ResponseEntity<ReservaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear reserva", description = "Tiene que ser a futuro, dentro del horario de atención y del máximo de personas configurado.")
    public ResponseEntity<ReservaResponseDTO> crear(@Valid @RequestBody ReservaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una reserva confirmada")
    public ResponseEntity<ReservaResponseDTO> actualizar(@PathVariable Long id,
                                                         @Valid @RequestBody ReservaRequestDTO dto) {
        return ResponseEntity.ok(reservaService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar reserva")
    public ResponseEntity<ReservaResponseDTO> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.cancelar(id));
    }

    @PatchMapping("/{id}/completar")
    @Operation(summary = "Marcar que el cliente llegó")
    public ResponseEntity<ReservaResponseDTO> completar(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.completar(id));
    }

    @PatchMapping("/{id}/no-asistio")
    @Operation(summary = "Marcar que el cliente no vino")
    public ResponseEntity<ReservaResponseDTO> marcarNoAsistio(@PathVariable Long id) {
        return ResponseEntity.ok(reservaService.marcarNoAsistio(id));
    }
}
