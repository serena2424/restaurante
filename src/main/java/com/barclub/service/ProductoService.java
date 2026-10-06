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
    // ---- Validación de lo que después se muestra en la web y el panel ----
    // La imagen y los nombres de las variantes se insertan en el HTML de la
    // página pública y del panel. Antes se guardaba cualquier texto: un link
    // de imagen con comillas (x" onerror="...) metía código que se ejecutaba
    // cuando el admin abría el Gestor de menú — un cajero podía llevarse así
    // la sesión del admin. Ahora el servidor solo acepta links de imagen
    // reales y nombres de variante sin caracteres de HTML.
    private static final java.util.regex.Pattern URL_IMAGEN_OK =
            java.util.regex.Pattern.compile("^(https?://|data:image/(png|jpe?g|gif|webp);base64,)[^\\s\"'<>`]*$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String validarImagen(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        if (!URL_IMAGEN_OK.matcher(u).matches()) {
            throw new BusinessException("El link de la imagen no es válido (tiene que empezar con http:// o https://).");
        }
        return u;
    }

    private String validarVariantes(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<java.util.Map<String, Object>> lista = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<java.util.Map<String, Object>>>() {});
            List<java.util.Map<String, Object>> limpias = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> v : lista) {
                String nombre = v.get("nombre") == null ? "" : String.valueOf(v.get("nombre")).trim();
                if (nombre.isEmpty()) continue;
                if (nombre.length() > 40 || nombre.matches(".*[<>\"'`].*")) {
                    throw new BusinessException("El nombre de variante \"" + nombre + "\" no es válido (máximo 40 caracteres, sin < > \" ').");
                }
                double precio;
                try { precio = Double.parseDouble(String.valueOf(v.get("precio"))); }
                catch (Exception e) { throw new BusinessException("La variante \"" + nombre + "\" necesita un precio válido."); }
                if (precio <= 0 || precio > 9999999) {
                    throw new BusinessException("El precio de la variante \"" + nombre + "\" tiene que estar entre $0 y $9.999.999.");
                }
                java.util.Map<String, Object> limpia = new java.util.LinkedHashMap<>();
                limpia.put("nombre", nombre);
                limpia.put("precio", precio);
                limpias.add(limpia);
            }
            return limpias.isEmpty() ? null : objectMapper.writeValueAsString(limpias);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("Las variantes del producto no tienen un formato válido.");
        }
    }

    private void validarTextos(ProductoRequestDTO dto) {
        if (dto.getNombre() != null && dto.getNombre().matches(".*[<>`].*")) {
            throw new BusinessException("El nombre del producto no puede tener los caracteres < > `");
        }
        if (dto.getCategoria() != null && dto.getCategoria().matches(".*[<>\"'`].*")) {
            throw new BusinessException("La categoría no puede tener los caracteres < > \" ' `");
        }
    }

    public ProductoResponseDTO crear(ProductoRequestDTO dto) {
        validarTextos(dto);
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
                .variantes(validarVariantes(dto.getVariantes()))
                .activo(dto.getActivo() != null ? dto.getActivo() : true)
                .imagenUrl(validarImagen(dto.getImagenUrl()))
                .categoria(dto.getCategoria())
                .build();
        return toDTO(productoRepository.save(producto));
    }

    // ---- Actualizar ----
    public ProductoResponseDTO actualizar(Long id, ProductoRequestDTO dto) {
        validarTextos(dto);
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
        producto.setVariantes(validarVariantes(dto.getVariantes()));
        if (dto.getActivo() != null) producto.setActivo(dto.getActivo());
        if (dto.getImagenUrl() != null) producto.setImagenUrl(validarImagen(dto.getImagenUrl()));
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
