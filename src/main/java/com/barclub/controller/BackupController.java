package com.barclub.controller;

import com.barclub.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /** Dispara un backup ahora mismo (requiere sesión iniciada). */
    @PostMapping("/ejecutar")
    public ResponseEntity<Map<String, Object>> ejecutar() {
        return ResponseEntity.ok(backupService.ejecutar());
    }

    /** Info del último backup y cuántos hay guardados. */
    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> estado() {
        return ResponseEntity.ok(backupService.estado());
    }

    /** Lista de backups guardados. */
    @GetMapping
    public ResponseEntity<java.util.List<Map<String, Object>>> listar() {
        return ResponseEntity.ok(backupService.listar());
    }

    /** Descarga un backup. */
    @GetMapping("/{archivo}")
    public ResponseEntity<byte[]> descargar(@org.springframework.web.bind.annotation.PathVariable String archivo) {
        try {
            byte[] datos = backupService.leer(archivo);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/sql")
                    .header("Content-Disposition", "attachment; filename=\"" + archivo + "\"")
                    .body(datos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
