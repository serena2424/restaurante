package com.barclub.service;

import com.barclub.dto.ProductoRequestDTO;
import com.barclub.dto.ProductoResponseDTO;
import com.barclub.entity.Producto;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductoService {

    private final ProductoRepository productoRepository;

    // ---- Listar todos ----
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> listarTodos() {
        return productoRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Solo activos (para el menú público) ----
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> listarActivos() {
        return productoRepository.findByActivoTrue()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Por categoría ----
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> listarPorCategoria(String categoria) {
        return productoRepository.findByCategoriaAndActivoTrue(categoria)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Buscar por nombre ----
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> buscarPorNombre(String nombre) {
        return productoRepository.findByNombreContainingIgnoreCase(nombre)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Obtener por id ----
    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerPorId(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        return toDTO(producto);
    }

    // ---- Crear ----
    public ProductoResponseDTO crear(ProductoRequestDTO dto) {
        if (productoRepository.existsByNombreIgnoreCaseAndCategoria(dto.getNombre().trim(), dto.getCategoria())) {
            throw new BusinessException(
                "Ya existe un producto llamado \"" + dto.getNombre().trim() + "\" en la categoría " + dto.getCategoria() + ".");
        }
        Producto producto = Producto.builder()
                .nombre(dto.getNombre().trim())
                .descripcion(dto.getDescripcion())
                .precio(dto.getPrecio())
                .costo(dto.getCosto())
                .precioEntera(dto.getPrecioEntera())
                .variantes(dto.getVariantes())
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .imagenUrl(dto.getImagenUrl())
                .categoria(dto.getCategoria())
                .build();
        return toDTO(productoRepository.save(producto));
    }

    // ---- Actualizar ----
    public ProductoResponseDTO actualizar(Long id, ProductoRequestDTO dto) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));

        if (productoRepository.existsByNombreIgnoreCaseAndCategoriaAndIdNot(dto.getNombre().trim(), dto.getCategoria(), id)) {
            throw new BusinessException(
                "Ya existe un producto llamado \"" + dto.getNombre().trim() + "\" en la categoría " + dto.getCategoria() + ".");
        }

        producto.setNombre(dto.getNombre().trim());
        producto.setDescripcion(dto.getDescripcion());
        producto.setPrecio(dto.getPrecio());
        producto.setCosto(dto.getCosto());
        producto.setPrecioEntera(dto.getPrecioEntera());
        producto.setVariantes(dto.getVariantes());
        if (dto.getActivo() != null) producto.setActivo(dto.getActivo());
        if (dto.getImagenUrl() != null) producto.setImagenUrl(dto.getImagenUrl());
        producto.setCategoria(dto.getCategoria());

        return toDTO(productoRepository.save(producto));
    }

    // ---- Activar / Desactivar ----
    public ProductoResponseDTO toggleActivo(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        producto.setActivo(!producto.getActivo());
        return toDTO(productoRepository.save(producto));
    }

    // ---- Eliminar ----
    public void eliminar(Long id) {
        if (!productoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Producto", id);
        }
        try {
            productoRepository.deleteById(id);
            productoRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // El producto figura en pedidos o ventas anteriores: borrarlo dejaría
            // ese historial incompleto. Se desactiva en su lugar.
            throw new BusinessException(
                "Este producto ya fue pedido alguna vez, así que no se puede borrar "
                + "sin perder el historial. Desactivalo para que deje de aparecer en la carta.");
        }
    }

    // ---- Mapper entidad -> DTO ----
    public ProductoResponseDTO toDTO(Producto p) {
        return ProductoResponseDTO.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .descripcion(p.getDescripcion())
                .precio(p.getPrecio())
                .costo(p.getCosto())
                .precioEntera(p.getPrecioEntera())
                .variantes(p.getVariantes())
                .activo(p.getActivo())
                .imagenUrl(p.getImagenUrl())
                .categoria(p.getCategoria())
                .build();
    }
}
