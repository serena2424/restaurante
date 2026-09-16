package com.barclub.controller;

import com.barclub.dto.ProductoRequestDTO;
import com.barclub.dto.ProductoResponseDTO;
import com.barclub.exception.ErrorResponse;
import com.barclub.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "ABM de productos del menú del bar")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @Operation(summary = "Listar todos los productos", description = "Incluye activos e inactivos. Para uso del panel admin.")
    @ApiResponse(responseCode = "200", description = "Lista completa de productos")
    public ResponseEntity<List<ProductoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(productoService.listarTodos());
    }

    @GetMapping("/activos")
    @Operation(summary = "Listar productos activos", description = "Solo productos visibles en el menú público.")
    @ApiResponse(responseCode = "200", description = "Lista de productos activos")
    public ResponseEntity<List<ProductoResponseDTO>> listarActivos() {
        return ResponseEntity.ok(productoService.listarActivos());
    }

    @GetMapping("/categoria/{categoria}")
    @Operation(summary = "Listar productos por categoría", description = "Filtra productos activos por categoría (ej: Pizzas, Bebidas).")
    @ApiResponse(responseCode = "200", description = "Lista filtrada por categoría")
    public ResponseEntity<List<ProductoResponseDTO>> listarPorCategoria(@PathVariable String categoria) {
        return ResponseEntity.ok(productoService.listarPorCategoria(categoria));
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar productos por nombre", description = "Búsqueda parcial e insensible a mayúsculas.")
    @ApiResponse(responseCode = "200", description = "Resultados de búsqueda")
    public ResponseEntity<List<ProductoResponseDTO>> buscar(@RequestParam String nombre) {
        return ResponseEntity.ok(productoService.buscarPorNombre(nombre));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener producto por ID")
    @ApiResponse(responseCode = "200", description = "Producto encontrado")
    @ApiResponse(responseCode = "404", description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ProductoResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerPorId(id));
    }

    @PostMapping
    @Operation(summary = "Crear producto", description = "Crea un nuevo producto en el menú. La imagen puede ser URL o base64.")
    @ApiResponse(responseCode = "201", description = "Producto creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre, precio o categoría faltantes)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ProductoResponseDTO> crear(@Valid @RequestBody ProductoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar producto")
    @ApiResponse(responseCode = "200", description = "Producto actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ProductoResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequestDTO dto) {
        return ResponseEntity.ok(productoService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Activar / desactivar producto", description = "Alterna el estado activo del producto sin eliminarlo.")
    @ApiResponse(responseCode = "200", description = "Estado alternado")
    @ApiResponse(responseCode = "404", description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ProductoResponseDTO> toggleActivo(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.toggleActivo(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar producto")
    @ApiResponse(responseCode = "204", description = "Producto eliminado")
    @ApiResponse(responseCode = "404", description = "Producto no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
