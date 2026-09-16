package com.barclub.service;

import com.barclub.dto.*;
import com.barclub.entity.*;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PedidoService {

    private static final Logger logger = LoggerFactory.getLogger(PedidoService.class);

    // ---- Resolución de precio por variante (nombre libre) ----
    // El precio de un pedido NUNCA sale de lo que mande el cliente — siempre
    // se recalcula acá contra lo que dice la base en este momento. Antes
    // solo reconocía el nombre fijo "Entera" (el viejo esquema exclusivo de
    // pizzas); con el sistema de variantes de nombre libre, cualquier otro
    // nombre (media, chico, grande, etc.) caía sin darse cuenta al precio
    // base del producto, cobrando mal. Ahora primero busca coincidencia por
    // nombre dentro de las variantes reales del producto.
    private Double resolverPrecioVariante(Producto producto, String variante) {
        if (variante == null || variante.isBlank()) return producto.getPrecio();
        String variantesJson = producto.getVariantes();
        if (variantesJson != null && !variantesJson.isBlank()) {
            try {
                List<java.util.Map<String, Object>> lista = objectMapper.readValue(
                        variantesJson, new com.fasterxml.jackson.core.type.TypeReference<List<java.util.Map<String, Object>>>() {});
                for (java.util.Map<String, Object> v : lista) {
                    Object nombreObj = v.get("nombre");
                    if (nombreObj != null && variante.trim().equalsIgnoreCase(String.valueOf(nombreObj).trim())) {
                        Object precioObj = v.get("precio");
                        if (precioObj != null) return Double.valueOf(String.valueOf(precioObj));
                    }
                }
                // El nombre no matchea ninguna variante actual del producto (por
                // ejemplo, se sacó o se renombró desde que el cliente armó el
                // carrito) — mejor avisar que cobrar cualquier precio sin que
                // nadie lo note.
                throw new BusinessException("La opción '" + variante + "' de '" + producto.getNombre()
                        + "' ya no está disponible. Volvé a elegir la opción desde el menú actualizado.");
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                logger.warn("No se pudo leer variantes de producto {} ({}), se usa precio base como respaldo: {}",
                        producto.getId(), producto.getNombre(), e.getMessage());
            }
        }
        // Esquema viejo (Media/Entera fijo, de antes del sistema de variantes libres)
        if ("Entera".equalsIgnoreCase(variante.trim()) && producto.getPrecioEntera() != null && producto.getPrecioEntera() > 0) {
            return producto.getPrecioEntera();
        }
        // No es una variante de precio (ej. elección de salsa/acompañamiento
        // que no cambia el precio) — se cobra el precio base, como siempre.
        return producto.getPrecio();
    }

    // Anti-duplicación: rechaza un pedido idéntico repetido en pocos segundos
    // (doble submit, reintento por red). No bloquea pedidos legítimos distintos.
    private static final java.util.Map<String, Long> ULTIMOS_ENVIOS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long VENTANA_ANTIDUP_MS = 8000;
    private static synchronized boolean duplicadoReciente(String clave) {
        long ahora = System.currentTimeMillis();
        ULTIMOS_ENVIOS.values().removeIf(t -> ahora - t > VENTANA_ANTIDUP_MS);
        Long prev = ULTIMOS_ENVIOS.get(clave);
        if (prev != null && ahora - prev < VENTANA_ANTIDUP_MS) return true;
        ULTIMOS_ENVIOS.put(clave, ahora);
        return false;
    }

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final com.barclub.service.ConfigLocalService configLocalService;
    private final ClienteService clienteService;
    private final UsuarioService usuarioService;
    private final com.barclub.websocket.RealtimeNotifier realtimeNotifier;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ---- Foto de los detalles ANTES de editar (para que Cocina vea el diff) ----
    // Se guarda una sola vez por "ciclo de edición": la primera vez que se toca
    // el pedido desde que quedó limpio (recién creado, o recién pasado a LISTO).
    // Si ya había una foto tomada en este ciclo, no se pisa.
    private void capturarSnapshotSiHaceFalta(Pedido pedido) {
        if (pedido.getModificadoEn() != null) return; // ya hay una edición en curso en este ciclo
        try {
            List<java.util.Map<String, Object>> snap = pedido.getDetalles().stream()
                    .map(d -> {
                        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("nombre", d.getProducto() != null ? d.getProducto().getNombre() : null);
                        m.put("categoria", d.getProducto() != null ? d.getProducto().getCategoria() : null);
                        m.put("variante", d.getVariante());
                        m.put("cantidad", d.getCantidad());
                        return m;
                    })
                    .collect(Collectors.toList());
            pedido.setDetalleSnapshotAntesEdicion(objectMapper.writeValueAsString(snap));
        } catch (Exception e) {
            logger.warn("No se pudo generar la foto de detalles antes de editar el pedido {}: {}", pedido.getId(), e.getMessage());
        }
    }

    // Devuelve el Usuario logueado en este request (vía JWT), o null si es
    // un pedido público sin sesión (cliente pidiendo desde la web).
    private Usuario usuarioAutenticadoActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        return usuarioRepository.findByEmail(auth.getName()).orElse(null);
    }

    // Un Mozo solo puede tocar los pedidos de sus propias mesas — nunca los
    // de otro mozo. Admin y Cajero pueden editar cualquiera, como siempre.
    private void verificarPuedeEditar(Pedido pedido) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;
        boolean esMozo = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MOZO".equals(a.getAuthority()));
        if (!esMozo) return;
        Usuario yo = usuarioAutenticadoActual();
        if (yo == null || pedido.getCreadoPorUsuarioId() == null || !pedido.getCreadoPorUsuarioId().equals(yo.getId())) {
            throw new BusinessException("Solo podés editar los pedidos de tus propias mesas.");
        }
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarActivos() {
        return pedidoRepository.findPedidosActivos().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPorEstado(EstadoPedido estado) {
        return pedidoRepository.findByEstado(estado).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPorFecha(LocalDate fecha) {
        return pedidoRepository.findByFecha(fecha).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtenerPorId(Long id) {
        return toDTO(pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id)));
    }

    public PedidoResponseDTO crear(PedidoRequestDTO dto) {
        logger.info("NUEVO PEDIDO: tipo={}, usuario={}, cliente='{}', productos={}",
                dto.getTipo(), dto.getUsuarioId(), dto.getNombreCliente(),
                dto.getDetalles() != null ? dto.getDetalles().size() : 0);

        // Los pedidos de la página pública no pertenecen a ningún empleado, pero el
        // sistema necesita asociarlos a un usuario. Si el indicado no existe (por
        // ejemplo, si ese usuario fue eliminado), usamos cualquier admin disponible
        // en lugar de rechazar el pedido: un cliente no debe quedarse sin pedir por
        // un cambio interno de usuarios.
        Usuario usuario = (dto.getUsuarioId() == null)
                ? null
                : usuarioRepository.findById(dto.getUsuarioId()).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.findAll().stream()
                    .filter(u -> u.getRol() == Rol.ADMIN)
                    .findFirst()
                    .orElseGet(() -> usuarioRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new BusinessException(
                                    "No hay usuarios cargados en el sistema")));
            logger.warn("Pedido recibido con usuarioId={} inexistente. Se asigna a {}",
                    dto.getUsuarioId(), usuario.getEmail());
        }

        // El rol MOZO solo puede registrar pedidos de mesa (LOCAL)
        if (usuario.getRol() == Rol.MOZO && dto.getTipo() != TipoPedido.LOCAL) {
            throw new BusinessException("El rol MOZO solo puede registrar pedidos de tipo LOCAL (mesa)");
        }

        // Todo pedido tiene que poder identificarse a la hora de entregarlo.
        // En retiro y delivery hace falta el nombre; en el local alcanza con la mesa.
        boolean sinNombre = dto.getNombreCliente() == null || dto.getNombreCliente().isBlank();
        boolean sinMesa   = dto.getMesa() == null || dto.getMesa().isBlank();
        if (dto.getTipo() == TipoPedido.LOCAL) {
            if (sinNombre && sinMesa) {
                throw new BusinessException("Indicá la mesa o el nombre del cliente");
            }
        } else if (sinNombre) {
            throw new BusinessException("El nombre del cliente es obligatorio");
        }

        if (dto.getTipo() == TipoPedido.DELIVERY
                && (dto.getDireccionEntrega() == null || dto.getDireccionEntrega().isBlank())) {
            logger.warn("PEDIDO RECHAZADO: delivery sin dirección de entrega. Usuario={}", dto.getUsuarioId());
            throw new BusinessException("El delivery requiere una dirección de entrega");
        }

        // Anti-duplicado: si llega un pedido idéntico (mismo tipo, teléfono, nombre
        // e ítems) en los últimos segundos, es un doble envío/reintento → se rechaza.
        if (dto.getDetalles() != null) {
            String firma = "PED|" + dto.getTipo() + "|"
                    + (dto.getTelefonoCliente() == null ? "" : dto.getTelefonoCliente().trim()) + "|"
                    + (dto.getNombreCliente() == null ? "" : dto.getNombreCliente().trim()) + "|"
                    + dto.getDetalles().stream()
                        .map(d -> d.getProductoId() + ":" + d.getCantidad() + ":" + (d.getVariante() == null ? "" : d.getVariante()))
                        .sorted().collect(Collectors.joining(","));
            if (duplicadoReciente(firma)) {
                throw new BusinessException("Ya recibimos este pedido hace unos segundos. Esperá un momento antes de reenviarlo.");
            }
        }

        // Quién lo cargó realmente: se prioriza el usuario autenticado del
        // panel (mozo/cajero/admin logueado) por sobre el usuarioId que
        // mande el cuerpo del pedido — igual que con los precios, nunca se
        // confía en un dato que podría venir manipulado desde afuera. Los
        // pedidos de la página pública (sin login) quedan sin dueño (null).
        Usuario creadoPor = usuarioAutenticadoActual();

        Pedido pedido = Pedido.builder()
                .fecha(LocalDate.now())
                .hora(LocalTime.now())
                .estado(EstadoPedido.PENDIENTE)
                .tipo(dto.getTipo())
                .total(0.0)
                .usuario(usuario)
                .creadoPorUsuarioId(creadoPor != null ? creadoPor.getId() : null)
                .creadoPorNombre(creadoPor != null ? creadoPor.getNombre() : null)
                .nombreCliente(dto.getNombreCliente())
                .telefonoCliente(dto.getTelefonoCliente())
                .direccionEntrega(dto.getDireccionEntrega())
                .horarioEntrega(dto.getHorarioEntrega())
                .mesa(dto.getMesa())
                .metodoPagoPreferido(dto.getMetodoPagoPreferido())
                .build();

        if (dto.getClienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.getClienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.getClienteId()));
            pedido.setCliente(cliente);
        }

        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        double total = 0.0;
        for (DetallePedidoRequestDTO detalleDTO : dto.getDetalles()) {
            Producto producto = productoRepository.findById(detalleDTO.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto", detalleDTO.getProductoId()));

            if (!producto.getActivo()) {
                logger.warn("PRODUCTO INACTIVO en pedido: productoId={}, nombre='{}'",
                        producto.getId(), producto.getNombre());
                throw new BusinessException("El producto '" + producto.getNombre() + "' no está disponible");
            }

            // Precio según la variante elegida — se resuelve siempre contra la
            // base (nunca contra lo que mande el cliente), tanto para el
            // esquema nuevo de variantes de nombre libre como el viejo
            // Media/Entera fijo.
            String variante = detalleDTO.getVariante();
            Double precioUnit = resolverPrecioVariante(producto, variante);

            DetallePedido detalle = DetallePedido.builder()
                    .pedido(pedidoGuardado)
                    .producto(producto)
                    .cantidad(detalleDTO.getCantidad())
                    .variante(variante != null && !variante.isBlank() ? variante.trim() : null)
                    .precioUnitario(precioUnit)
                    .costoUnitario(producto.getCosto())
                    .subtotal(precioUnit * detalleDTO.getCantidad())
                    .build();

            pedidoGuardado.getDetalles().add(detalle);
            total += detalle.getSubtotal();
        }

        // Costo de envío: solo en delivery, tomado de la configuración del local.
        double costoEnvio = 0.0;
        if (pedidoGuardado.getTipo() == TipoPedido.DELIVERY) {
            Integer cd = configLocalService.obtener().getCostoDelivery();
            costoEnvio = (cd != null) ? cd.doubleValue() : 0.0;
            total += costoEnvio;
        }
        pedidoGuardado.setCostoEnvio(costoEnvio);
        pedidoGuardado.setTotal(total);
        Pedido resultado = pedidoRepository.save(pedidoGuardado);
        logger.info("PEDIDO CREADO: id={}, total=${}, tipo={}", resultado.getId(), resultado.getTotal(), resultado.getTipo());
        realtimeNotifier.avisarPedidos();
        return toDTO(resultado);
    }

    // Pedidos entregados desde el último cierre de caja. Igual en todos los
    // dispositivos, porque el filtro lo hace el servidor (no el navegador).
    public List<PedidoResponseDTO> entregadosDesdeCierre() {
        java.time.LocalDateTime desde;
        try {
            String c = configLocalService.obtener().getCierreCaja();
            desde = (c == null || c.isBlank())
                    ? java.time.LocalDate.now().atStartOfDay()
                    : java.time.LocalDateTime.parse(c);
        } catch (Exception e) {
            desde = java.time.LocalDate.now().atStartOfDay();
        }
        return pedidoRepository.findEntregadosDesde(desde).stream()
                .map(this::toDTO).collect(java.util.stream.Collectors.toList());
    }

    public PedidoResponseDTO cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        EstadoPedido estadoAnterior = pedido.getEstado();
        // ENTREGADO solo lo pone el cobro (al registrar la venta), nunca este
        // endpoint, para que no queden pedidos entregados sin venta registrada.
        if (nuevoEstado == EstadoPedido.ENTREGADO) {
            throw new BusinessException("El pedido se marca como entregado al cobrarlo, no por este medio.");
        }
        validarTransicionEstado(estadoAnterior, nuevoEstado);

        pedido.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoPedido.ENTREGADO && pedido.getEntregadoEn() == null) {
            pedido.setEntregadoEn(java.time.LocalDateTime.now());
        }
        // Al pasar a LISTO, Cocina ya vio y resolvió cualquier cambio pendiente:
        // se limpia la marca de "modificado" y la foto de diff, así el próximo
        // ciclo de edición (si el pedido vuelve a tocarse) arranca de cero.
        if (nuevoEstado == EstadoPedido.LISTO) {
            pedido.setModificadoEn(null);
            pedido.setDetalleSnapshotAntesEdicion(null);
        }
        logger.info("ESTADO PEDIDO: id={} -> {} -> {}", id, estadoAnterior, nuevoEstado);
        PedidoResponseDTO resultado = toDTO(pedidoRepository.save(pedido));
        realtimeNotifier.avisarPedidos();
        return resultado;
    }

    public PedidoResponseDTO cancelar(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", id));

        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            throw new BusinessException("No se puede cancelar un pedido ya entregado");
        }
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new BusinessException("El pedido ya está cancelado");
        }
        // Se puede cancelar mientras no esté cobrado. Antes solo se permitía en
        // PENDIENTE, y un pedido cargado por error quedaba trabado para siempre.
        if (pedido.getEstado() != EstadoPedido.PENDIENTE
                && pedido.getEstado() != EstadoPedido.PREPARACION
                && pedido.getEstado() != EstadoPedido.LISTO) {
            throw new BusinessException("No se puede cancelar un pedido en estado: " + pedido.getEstado());
        }

        // Antes había un límite de 30 minutos desde la creación para poder cancelar.
        // Problema de negocio: si un pedido viejo no se puede cancelar, la única forma
        // de sacarlo de la tabla era "Cobrarlo", y eso dejaba un monto registrado que
        // nadie pagó. Por eso se quita el límite de tiempo: se puede cancelar sin
        // importar la antigüedad, mientras el pedido no esté ENTREGADO ni CANCELADO
        // (chequeado arriba).

        pedido.setEstado(EstadoPedido.CANCELADO);
        // Se reutiliza modificadoEn para saber CUÁNDO se canceló (Cocina lo
        // usa para mostrar el sello "CANCELADO" un rato después de que pasa,
        // en vez de que el pedido desaparezca del tablero sin ningún aviso).
        pedido.setModificadoEn(java.time.LocalDateTime.now());
        logger.info("PEDIDO CANCELADO: id={}, cliente='{}'", id, pedido.getNombreCliente());
        PedidoResponseDTO resultado = toDTO(pedidoRepository.save(pedido));
        realtimeNotifier.avisarPedidos();
        return resultado;
    }

    public void eliminarEntregadosHoy() {
        List<Pedido> entregados = pedidoRepository.findByFecha(LocalDate.now())
                .stream()
                .filter(p -> p.getEstado() == EstadoPedido.ENTREGADO)
                .collect(Collectors.toList());

        logger.info("LIMPIEZA DIARIA: eliminando {} pedidos entregados de hoy", entregados.size());

        for (Pedido p : entregados) {
            ventaRepository.findByPedidoId(p.getId()).ifPresent(ventaRepository::delete);
            pedidoRepository.delete(p);
        }
    }

    private boolean esEditable(EstadoPedido estado) {
        // Se puede seguir editando (productos y datos del cliente) mientras
        // está PENDIENTE o en PREPARACION. Una vez LISTO se bloquea — ya se
        // está por entregar, no tiene sentido seguir tocándolo.
        return estado == EstadoPedido.PENDIENTE || estado == EstadoPedido.PREPARACION;
    }

    public PedidoResponseDTO agregarDetalle(Long pedidoId, DetallePedidoRequestDTO detalleDTO) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));
        verificarPuedeEditar(pedido);

        if (!esEditable(pedido.getEstado())) {
            throw new BusinessException("Solo se pueden modificar pedidos en estado PENDIENTE o PREPARACION");
        }

        Producto producto = productoRepository.findById(detalleDTO.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", detalleDTO.getProductoId()));

        // El precio depende de la variante elegida — se resuelve siempre
        // contra la base, buscando coincidencia por nombre en las variantes
        // reales del producto (sistema nuevo, nombre libre), con
        // compatibilidad para el esquema viejo fijo de Media/Entera.
        String variante = detalleDTO.getVariante();
        double precio = resolverPrecioVariante(producto, variante);

        logger.info("AGREGAR DETALLE: pedidoId={}, productoId={}, variante={}, cantidad={}",
                pedidoId, detalleDTO.getProductoId(), variante, detalleDTO.getCantidad());

        capturarSnapshotSiHaceFalta(pedido);

        // Fusiona con un detalle ya cargado solo si es el MISMO producto Y
        // la MISMA variante — antes fusionaba por producto nomás, así que
        // "Fugazzeta Media" y "Fugazzeta Entera" se mezclaban en una sola
        // línea con la cantidad sumada, perdiendo cuál era cuál.
        pedido.getDetalles().stream()
                .filter(d -> d.getProducto() != null && d.getProducto().getId().equals(producto.getId())
                        && java.util.Objects.equals(d.getVariante(), variante))
                .findFirst()
                .ifPresentOrElse(
                        detalle -> {
                            detalle.setCantidad(detalle.getCantidad() + detalleDTO.getCantidad());
                            detalle.setSubtotal(detalle.getPrecioUnitario() * detalle.getCantidad());
                        },
                        () -> {
                            DetallePedido nuevo = DetallePedido.builder()
                                    .pedido(pedido)
                                    .producto(producto)
                                    .cantidad(detalleDTO.getCantidad())
                                    .variante(variante)
                                    .precioUnitario(precio)
                                    .subtotal(precio * detalleDTO.getCantidad())
                                    .build();
                            pedido.getDetalles().add(nuevo);
                        }
                );

        pedido.setModificadoEn(java.time.LocalDateTime.now());
        recalcularTotal(pedido);
        PedidoResponseDTO resultado = toDTO(pedidoRepository.save(pedido));
        realtimeNotifier.avisarPedidos();
        return resultado;
    }

    // ---- Cambiar la cantidad de un producto ya cargado en el pedido ----
    // Si la nueva cantidad es 0 o menos, directamente se saca el producto
    // del pedido (mismo resultado que eliminarDetalle).
    public PedidoResponseDTO cambiarCantidadDetalle(Long pedidoId, Long detalleId, int nuevaCantidad) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));
        verificarPuedeEditar(pedido);

        if (!esEditable(pedido.getEstado())) {
            throw new BusinessException("Solo se pueden modificar pedidos en estado PENDIENTE o PREPARACION");
        }

        capturarSnapshotSiHaceFalta(pedido);

        if (nuevaCantidad <= 0) {
            pedido.getDetalles().removeIf(d -> d.getId().equals(detalleId));
        } else {
            DetallePedido detalle = pedido.getDetalles().stream()
                    .filter(d -> d.getId().equals(detalleId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Detalle de pedido", detalleId));
            detalle.setCantidad(nuevaCantidad);
            detalle.setSubtotal(detalle.getPrecioUnitario() * nuevaCantidad);
        }

        pedido.setModificadoEn(java.time.LocalDateTime.now());
        recalcularTotal(pedido);
        PedidoResponseDTO resultado = toDTO(pedidoRepository.save(pedido));
        realtimeNotifier.avisarPedidos();
        return resultado;
    }

    // ---- Editar los datos del cliente de un pedido ya creado ----
    // Para corregir un dato mal tipeado por el cliente (nombre, teléfono,
    // dirección, mesa) sin tener que cancelar y rehacer todo el pedido.
    // Cada campo es opcional: solo se actualiza el que venga con valor.
    public PedidoResponseDTO actualizarDatosCliente(Long pedidoId, com.barclub.dto.PedidoDatosClienteDTO dto) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));
        verificarPuedeEditar(pedido);

        if (!esEditable(pedido.getEstado())) {
            throw new BusinessException("Solo se pueden modificar pedidos en estado PENDIENTE o PREPARACION");
        }

        if (dto.getNombreCliente() != null) pedido.setNombreCliente(dto.getNombreCliente());
        if (dto.getTelefonoCliente() != null) pedido.setTelefonoCliente(dto.getTelefonoCliente());
        if (dto.getDireccionEntrega() != null) pedido.setDireccionEntrega(dto.getDireccionEntrega());
        if (dto.getMesa() != null) pedido.setMesa(dto.getMesa());

        pedido.setModificadoEn(java.time.LocalDateTime.now());
        logger.info("DATOS CLIENTE ACTUALIZADOS: pedidoId={}", pedidoId);
        PedidoResponseDTO resultado = toDTO(pedidoRepository.save(pedido));
        realtimeNotifier.avisarPedidos();
        return resultado;
    }

    public PedidoResponseDTO eliminarDetalle(Long pedidoId, Long detalleId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", pedidoId));
        verificarPuedeEditar(pedido);

        if (!esEditable(pedido.getEstado())) {
            throw new BusinessException("Solo se pueden modificar pedidos en estado PENDIENTE o PREPARACION");
        }

        capturarSnapshotSiHaceFalta(pedido);
        pedido.getDetalles().removeIf(d -> d.getId().equals(detalleId));
        pedido.setModificadoEn(java.time.LocalDateTime.now());
        recalcularTotal(pedido);
        PedidoResponseDTO resultado = toDTO(pedidoRepository.save(pedido));
        realtimeNotifier.avisarPedidos();
        return resultado;
    }

    private void recalcularTotal(Pedido pedido) {
        double total = pedido.getDetalles().stream().mapToDouble(DetallePedido::getSubtotal).sum();
        pedido.setTotal(total);
    }

    private void validarTransicionEstado(EstadoPedido actual, EstadoPedido nuevo) {
        boolean valido = switch (actual) {
            // Se puede avanzar al paso siguiente, y también marcar ENTREGADO desde
            // cualquier estado activo: cuando el cajero cobra, el pedido se entrega,
            // sin importar si venía de pendiente, preparación o listo.
            case PENDIENTE -> nuevo == EstadoPedido.PREPARACION
                    || nuevo == EstadoPedido.ENTREGADO
                    || nuevo == EstadoPedido.CANCELADO;
            case PREPARACION -> nuevo == EstadoPedido.LISTO
                    || nuevo == EstadoPedido.ENTREGADO
                    || nuevo == EstadoPedido.CANCELADO;
            case LISTO -> nuevo == EstadoPedido.ENTREGADO
                    || nuevo == EstadoPedido.CANCELADO;
            case ENTREGADO, CANCELADO -> false;
        };
        if (!valido) {
            logger.warn("TRANSICIÓN INVÁLIDA: {} -> {}", actual, nuevo);
            throw new BusinessException("Transición de estado inválida: " + actual + " -> " + nuevo);
        }
    }

    // Mismo criterio que cancelar(): cancelable mientras no esté ENTREGADO ni
    // CANCELADO, sin límite de tiempo desde la creación.
    private boolean esCancelable(Pedido pedido) {
        return pedido.getEstado() == EstadoPedido.PENDIENTE
                || pedido.getEstado() == EstadoPedido.PREPARACION
                || pedido.getEstado() == EstadoPedido.LISTO;
    }

    public PedidoResponseDTO toDTO(Pedido p) {
        List<DetallePedidoResponseDTO> detalles = p.getDetalles().stream()
                .map(d -> DetallePedidoResponseDTO.builder()
                        .id(d.getId())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        // Si el producto fue borrado del menú, el pedido histórico
                        // igual tiene que poder verse (cantidad, precio y subtotal
                        // quedaron guardados en el detalle).
                        .variante(d.getVariante())
                        .producto(d.getProducto() != null ? productoService.toDTO(d.getProducto()) : null)
                        .build())
                .collect(Collectors.toList());

        return PedidoResponseDTO.builder()
                .id(p.getId())
                .fecha(p.getFecha())
                .hora(p.getHora())
                .estado(p.getEstado())
                .tipo(p.getTipo())
                .total(p.getTotal())
                .costoEnvio(p.getCostoEnvio())
                .nombreCliente(p.getNombreCliente())
                .telefonoCliente(p.getTelefonoCliente())
                .direccionEntrega(p.getDireccionEntrega())
                .horarioEntrega(p.getHorarioEntrega())
                .mesa(p.getMesa())
                .creadoPorUsuarioId(p.getCreadoPorUsuarioId())
                .creadoPorNombre(p.getCreadoPorNombre())
                .modificadoEn(p.getModificadoEn())
                .detalleSnapshotAntesEdicion(p.getDetalleSnapshotAntesEdicion())
                .metodoPagoPreferido(p.getMetodoPagoPreferido())
                .cliente(p.getCliente() != null ? clienteService.toDTO(p.getCliente()) : null)
                .usuario(p.getUsuario() != null ? usuarioService.toDTO(p.getUsuario()) : null)
                .detalles(detalles)
                .cancelable(esCancelable(p))
                .build();
    }
}
