package com.barclub.controller;

import com.barclub.dto.ResumenDiaDTO;
import com.barclub.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Resumen de actividad diaria del bar")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumen")
    @Operation(summary = "Resumen de hoy", description = "Devuelve total de ventas, pedidos activos, en preparación, entregados y reservas del día.")
    @ApiResponse(responseCode = "200", description = "Resumen del día actual")
    public ResponseEntity<ResumenDiaDTO> resumenHoy() {
        return ResponseEntity.ok(dashboardService.resumenDelDia(LocalDate.now()));
    }

    @GetMapping("/resumen/fecha")
    @Operation(summary = "Resumen por fecha", description = "Igual al resumen de hoy pero para una fecha específica. Formato: YYYY-MM-DD.")
    @ApiResponse(responseCode = "200", description = "Resumen de la fecha indicada")
    public ResponseEntity<ResumenDiaDTO> resumenPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(dashboardService.resumenDelDia(fecha));
    }
}
