package com.gestorgastronomico.controller;

import com.gestorgastronomico.entity.ConfigLocal;
import com.gestorgastronomico.service.ConfigLocalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Configuración", description = "Datos del local, horarios, delivery y apariencia")
public class ConfigLocalController {

    private final ConfigLocalService service;

    @GetMapping
    @Operation(summary = "Configuración completa, con logo y portada")
    public ResponseEntity<ConfigLocal> obtener() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.obtener());
    }

    @GetMapping("/estado")
    @Operation(summary = "Configuración sin imágenes", description = "Para refrescar horarios y estado sin volver a bajar el logo y la portada.")
    public ResponseEntity<ConfigLocal> obtenerSinImagenes() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.obtenerSinImagenes());
    }

    @PatchMapping("/estado-manual")
    @Operation(summary = "Abrir, cerrar o volver a automático (solo admin)")
    public ResponseEntity<ConfigLocal> cambiarEstadoManual(@RequestParam String modo) {
        return ResponseEntity.ok(service.cambiarEstadoManual(modo));
    }

    @PatchMapping("/categorias-sin-cocina")
    @Operation(summary = "Categorías que no pasan por cocina, como las bebidas (solo admin)")
    public ResponseEntity<ConfigLocal> guardarCategoriasSinCocina(@RequestBody List<String> categorias) {
        return ResponseEntity.ok(service.guardarCategoriasSinCocina(categorias));
    }

    @PutMapping
    @Operation(summary = "Guardar configuración (solo admin)")
    public ResponseEntity<ConfigLocal> guardar(@RequestBody ConfigLocal cfg) {
        return ResponseEntity.ok(service.guardarDesdePanel(cfg));
    }
}
