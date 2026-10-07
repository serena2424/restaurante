package com.gestorgastronomico.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestorgastronomico.dto.*;
import com.gestorgastronomico.entity.*;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.exception.ResourceNotFoundException;
import com.gestorgastronomico.repository.*;
import com.gestorgastronomico.util.Dinero;
import com.gestorgastronomico.websocket.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PedidoService {

    private static final Logger logger = LoggerFactory.getLogger(PedidoService.class);
    private static final int MAX_LARGO_OPCION = 60;
    private static final int MESA_MAXIMA = 999;
    private static final int CANTIDAD_MAXIMA = 500;
    private static final int HORAS_CANCELADOS_VISIBLES_EN_COCINA = 24;

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoService productoService;
    private final ConfigLocalService configLocalService;
    private final ClienteService clienteService;
    private final RealtimeNotifier realtimeNotifier;
    private final ObjectMapper objectMapper;
    private final EnvioDuplicado envioDuplicado;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return aDTOs(filtrarSegunRol(pedidoRepository.findAll()));
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarActivos() {
        return aDTOs(filtrarSegunRol(pedidoRepository.findPedidosActivos()));
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPorEstado(EstadoPedido estado) {
        List<Pedido> pedidos = pedidoRepository.findByEstado(estado);
        if (tieneRol(Rol.COCINA)) {
            pedidos = estado == EstadoPedido.CANCELADO ? canceladosRecientes(pedidos) : List.of();
        }
        return aDTOs(filtrarSegunRol(pedidos));
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPorFecha(LocalDate fecha) {
        return aDTOs(filtrarSegunRol(pedidoRepository.findByFecha(fecha)));
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO obtenerPorId(Long id) {
        Pedido pedido = buscar(id);
        if (filtrarSegunRol(List.of(pedido)).isEmpty()) throw new ResourceNotFoundException("Pedido", id);
        return toDTO(pedido);
    }

    /** Pedidos entregados desde que se abrió la caja actual. */
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> entregadosDesdeCierre() {
        LocalDateTime desde = LocalDate.now(clock).atStartOfDay();
        String apertura = configLocalService.obtener().getCierreCaja();
        if (apertura != null && !apertura.isBlank()) {
            try {
                desde = LocalDateTime.parse(apertura);
            } catch (Exception e) {
                logger.warn("Inicio de caja ilegible ({}), se usa el comienzo del día", apertura);
            }
        }
        return aDTOs(filtrarSegunRol(pedidoRepository.findEntregadosDesde(desde)));
    }

    public PedidoResponseDTO crear(PedidoRequestDTO dto) {
        Usuario autenticado = usuarioAutenticado();
        boolean esPublico = autenticado == null;
        Usuario responsable = esPublico ? primerAdminActivo() : autenticado;

        if (!esPublico && responsable.getRol() == Rol.COCINA) {
            throw new BusinessException("Cocina no carga pedidos.");
        }
        if (responsable.getRol() == Rol.MOZO && dto.getTipo() != TipoPedido.LOCAL) {
            throw new BusinessException("El mozo solo puede cargar pedidos de mesa.");
        }
        if (esPublico) validarReglasDelLocal(dto);

        String mesa = normalizarMesa(dto.getMesa());
        if (responsable.getRol() == Rol.MOZO && mesa == null) {
            throw new BusinessException("Falta el número de mesa.");
        }
        validarIdentificacion(dto, mesa);

        if (envioDuplicado.esRepetido(firma(dto, autenticado))) {
            throw new BusinessException("Ya recibimos este pedido hace unos segundos. Esperá un momento antes de reenviarlo.");
        }

        boolean comandaDeMesa = !esPublico && dto.getTipo() == TipoPedido.LOCAL && mesa != null;
        if (comandaDeMesa) {
            Optional<Pedido> abierta = pedidoRepository.findComandasAbiertasDeMesa(mesa).stream().findFirst();
            if (abierta.isPresent()) return sumarAComandaAbierta(abierta.get(), dto, responsable);
        }

        Pedido pedido = Pedido.builder()
                .fecha(LocalDate.now(clock))
                .hora(LocalTime.now(clock))
                .estado(EstadoPedido.PENDIENTE)
                .tipo(dto.getTipo())
                .usuario(responsable)
                .creadoPorUsuarioId(autenticado != null ? autenticado.getId() : null)
                .creadoPorNombre(autenticado != null ? autenticado.getNombre() : null)
                .nombreCliente(dto.getNombreCliente())
                .telefonoCliente(dto.getTelefonoCliente())
                .direccionEntrega(dto.getDireccionEntrega())
                .horarioEntrega(dto.getHorarioEntrega())
                .mesa(mesa)
                .metodoPagoPreferido(dto.getMetodoPagoPreferido())
                .porTarjetas(comandaDeMesa ? Boolean.TRUE : null)
                .build();

        if (dto.getClienteId() != null) {
            pedido.setCliente(clienteRepository.findById(dto.getClienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.getClienteId())));
        }

        if (comandaDeMesa) {
            agregarTarjetas(pedido, dto.getDetalles());
        } else {
            for (DetallePedidoRequestDTO item : dto.getDetalles()) {
                Producto producto = productoActivo(item.getProductoId());
                String variante = normalizarVariante(item.getVariante());
                double precio = precioDe(producto, variante);
                pedido.getDetalles().add(nuevoDetalle(pedido, producto, variante, precio, item.getCantidad()));
            }
        }

        pedido.setCostoEnvio(dto.getTipo() == TipoPedido.DELIVERY ? costoEnvioActual() : 0.0);
        recalcularTotal(pedido);
        ComandaMesa.recalcularEstado(pedido, LocalDateTime.now(clock));

        Pedido guardado = pedidoRepository.save(pedido);
        logger.info("Pedido #{} creado: {} por ${}", guardado.getId(), guardado.getTipo(), guardado.getTotal());
        realtimeNotifier.avisarPedidos();
        return toDTO(guardado);
    }

    /**
     * Avance del pedido en cocina. ENTREGADO solo se permite si ya está cobrado
     * (si no, se entrega al cobrarlo) y CANCELADO tiene su propia operación.
     */
    public PedidoResponseDTO cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = buscar(id);
        if (pedido.esComandaPorTarjetas()) {
            throw new BusinessException("Esta mesa se maneja por tarjetas: cambiá el estado de cada plato.");
        }
        if (nuevoEstado == EstadoPedido.CANCELADO) {
            throw new BusinessException("Para cancelar un pedido usá el botón Cancelar.");
        }
        if (nuevoEstado == EstadoPedido.ENTREGADO && !pedido.estaPagado()) {
            throw new BusinessException("El pedido se marca como entregado al cobrarlo.");
        }
        EstadoPedido anterior = pedido.getEstado();
        validarTransicion(anterior, nuevoEstado);

        pedido.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoPedido.ENTREGADO) {
            pedido.setEntregadoEn(LocalDateTime.now(clock));
        }
        if (nuevoEstado == EstadoPedido.LISTO) {
            pedido.setModificadoEn(null);
            pedido.setDetalleSnapshotAntesEdicion(null);
        }
        logger.info("Pedido #{}: {} -> {}", id, anterior, nuevoEstado);
        return guardarYAvisar(pedido);
    }

    public PedidoResponseDTO cancelar(Long id) {
        Pedido pedido = buscar(id);
        verificarPuedeEditar(pedido);
        if (tieneRol(Rol.MOZO) || tieneRol(Rol.COCINA)) {
            if (!pedido.esComandaPorTarjetas()) throw new BusinessException("Este pedido lo cancela el cajero.");
            if (pedido.getDetalles().stream().anyMatch(d -> d.getEstado() == EstadoPedido.ENTREGADO)) {
                throw new BusinessException("Esta mesa ya tiene platos entregados. Sacá lo que no quieran o pedile al cajero que la cancele.");
            }
        }
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new BusinessException("El pedido ya está cancelado");
        }
        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            throw new BusinessException("No se puede cancelar un pedido ya entregado");
        }
        if (pedido.estaPagado()) {
            throw new BusinessException("El pedido ya está cobrado. Para cancelarlo, primero el admin tiene que anular el cobro.");
        }
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedido.setModificadoEn(LocalDateTime.now(clock));
        logger.info("Pedido #{} cancelado", id);
        return guardarYAvisar(pedido);
    }

    public PedidoResponseDTO agregarDetalle(Long pedidoId, DetallePedidoRequestDTO item) {
        Pedido pedido = buscarEditable(pedidoId);
        if (pedido.esComandaPorTarjetas()) {
            agregarTarjetas(pedido, List.of(item));
            recalcularTotal(pedido);
            ComandaMesa.recalcularEstado(pedido, LocalDateTime.now(clock));
            return guardarYAvisar(pedido);
        }
        Producto producto = productoActivo(item.getProductoId());
        String variante = normalizarVariante(item.getVariante());
        double precio = precioDe(producto, variante);

        capturarSnapshotSiHaceFalta(pedido);
        pedido.getDetalles().stream()
                .filter(d -> d.getProducto() != null
                        && d.getProducto().getId().equals(producto.getId())
                        && Objects.equals(d.getVariante(), variante))
                .findFirst()
                .ifPresentOrElse(
                        existente -> actualizarCantidad(existente, existente.getCantidad() + item.getCantidad()),
                        () -> pedido.getDetalles().add(nuevoDetalle(pedido, producto, variante, precio, item.getCantidad())));

        return guardarEdicion(pedido);
    }

    /** Cambia la cantidad de una línea. Con 0 o menos, la línea se quita. */
    public PedidoResponseDTO cambiarCantidadDetalle(Long pedidoId, Long detalleId, int nuevaCantidad) {
        Pedido pedido = buscarEditable(pedidoId);
        if (pedido.esComandaPorTarjetas()) {
            if (nuevaCantidad <= 0) return cancelarTarjeta(pedidoId, detalleId);
            DetallePedido tarjeta = tarjeta(pedido, detalleId);
            if (tarjeta.estaCancelada()) throw new BusinessException("Ese plato está cancelado. Si lo quieren, agregalo de nuevo.");
            if (tarjeta.getEstado() != EstadoPedido.PENDIENTE && tarjeta.getEstado() != EstadoPedido.PREPARACION) {
                throw new BusinessException("Ese plato ya está listo o entregado. Si piden más, agregalo como uno nuevo.");
            }
            actualizarCantidad(tarjeta, nuevaCantidad);
            tarjeta.setModificadoEn(LocalDateTime.now(clock));
            recalcularTotal(pedido);
            return guardarYAvisar(pedido);
        }
        capturarSnapshotSiHaceFalta(pedido);
        if (nuevaCantidad <= 0) {
            pedido.getDetalles().removeIf(d -> d.getId().equals(detalleId));
        } else {
            DetallePedido detalle = pedido.getDetalles().stream()
                    .filter(d -> d.getId().equals(detalleId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Detalle de pedido", detalleId));
            actualizarCantidad(detalle, nuevaCantidad);
        }
        return guardarEdicion(pedido);
    }

    public PedidoResponseDTO eliminarDetalle(Long pedidoId, Long detalleId) {
        if (buscar(pedidoId).esComandaPorTarjetas()) return cancelarTarjeta(pedidoId, detalleId);
        Pedido pedido = buscarEditable(pedidoId);
        capturarSnapshotSiHaceFalta(pedido);
        pedido.getDetalles().removeIf(d -> d.getId().equals(detalleId));
        return guardarEdicion(pedido);
    }

    /**
     * Avanza una tarjeta de una comanda de mesa. Cocina: NUEVO → En preparación
     * → Listo (y Listo puede volver a preparación). Mozo: Listo → Entregado, y
     * las bebidas de NUEVO a Entregado. Cajero y admin pueden todo.
     */
    public PedidoResponseDTO cambiarEstadoTarjeta(Long pedidoId, Long detalleId, EstadoPedido nuevo) {
        Pedido pedido = buscarComanda(pedidoId);
        verificarPuedeEditar(pedido);
        DetallePedido tarjeta = tarjeta(pedido, detalleId);
        if (nuevo == EstadoPedido.CANCELADO) return cancelarTarjeta(pedidoId, detalleId);
        if (pedido.getEstado() == EstadoPedido.CANCELADO) throw new BusinessException("La comanda está cancelada.");

        EstadoPedido actual = tarjeta.getEstado();
        if (actual == null) throw new BusinessException("Ese producto no es una tarjeta de la comanda.");
        boolean cierreDeMesaCobrada = pedido.estaPagado() && nuevo == EstadoPedido.ENTREGADO
                && actual != EstadoPedido.ENTREGADO && actual != EstadoPedido.CANCELADO
                && (tieneRol(Rol.ADMIN) || tieneRol(Rol.CAJERO));
        boolean valido = cierreDeMesaCobrada || (tarjeta.pasaPorCocina()
                ? switch (actual) {
                    case PENDIENTE -> nuevo == EstadoPedido.PREPARACION;
                    case PREPARACION -> nuevo == EstadoPedido.LISTO;
                    case LISTO -> nuevo == EstadoPedido.PREPARACION || nuevo == EstadoPedido.ENTREGADO;
                    case ENTREGADO, CANCELADO -> false;
                }
                : actual == EstadoPedido.PENDIENTE && nuevo == EstadoPedido.ENTREGADO);
        if (!valido) {
            throw new BusinessException("Ese plato no puede pasar de " + nombreEstado(actual) + " a " + nombreEstado(nuevo) + ".");
        }
        boolean entrega = nuevo == EstadoPedido.ENTREGADO;
        if (tieneRol(Rol.COCINA) && entrega) {
            throw new BusinessException("Cocina lo marca como listo; lo entrega el mozo.");
        }
        if (tieneRol(Rol.MOZO) && !entrega) {
            throw new BusinessException("Los estados de cocina los cambia cocina.");
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        tarjeta.setEstado(nuevo);
        tarjeta.setEstadoEn(ahora);
        if (nuevo == EstadoPedido.LISTO) tarjeta.setModificadoEn(null);
        ComandaMesa.recalcularEstado(pedido, ahora);
        logger.info("Mesa {} (pedido #{}), tarjeta {}: {} -> {}", pedido.getMesa(), pedidoId, detalleId, actual, nuevo);
        return guardarYAvisar(pedido);
    }

    /**
     * Saca un plato de la comanda: queda tachado y deja de sumar. Si ya estaba
     * entregado, solo lo puede sacar el cajero o el admin.
     */
    public PedidoResponseDTO cancelarTarjeta(Long pedidoId, Long detalleId) {
        Pedido pedido = buscarComanda(pedidoId);
        verificarPuedeEditar(pedido);
        DetallePedido tarjeta = tarjeta(pedido, detalleId);
        if (pedido.estaPagado()) {
            throw new BusinessException("La mesa ya está cobrada. Para sacar un plato, primero el admin tiene que anular el cobro.");
        }
        if (pedido.getEstado() == EstadoPedido.CANCELADO) throw new BusinessException("La comanda ya está cancelada.");
        if (tarjeta.estaCancelada()) throw new BusinessException("Ese plato ya está cancelado.");
        if (tarjeta.getEstado() == EstadoPedido.ENTREGADO && !tieneRol(Rol.ADMIN) && !tieneRol(Rol.CAJERO)) {
            throw new BusinessException("Ese plato ya se entregó. Para sacarlo de la cuenta, pedíselo al cajero.");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        tarjeta.setEstado(EstadoPedido.CANCELADO);
        tarjeta.setEstadoEn(ahora);
        recalcularTotal(pedido);
        ComandaMesa.recalcularEstado(pedido, ahora);
        logger.info("Mesa {} (pedido #{}): tarjeta {} cancelada", pedido.getMesa(), pedidoId, detalleId);
        return guardarYAvisar(pedido);
    }

    /** Suma lo que cargó el mozo a la comanda abierta de esa mesa, como tarjetas nuevas. */
    private PedidoResponseDTO sumarAComandaAbierta(Pedido comanda, PedidoRequestDTO dto, Usuario responsable) {
        if (responsable.getRol() == Rol.MOZO && !esDelMozo(comanda, responsable, idsDeMozos())) {
            String quien = comanda.getCreadoPorNombre() != null ? comanda.getCreadoPorNombre() : "otro mozo";
            throw new BusinessException("La mesa " + comanda.getMesa() + " la atiende " + quien
                    + ". Que lo cargue " + quien + " o el cajero.");
        }
        agregarTarjetas(comanda, dto.getDetalles());
        recalcularTotal(comanda);
        ComandaMesa.recalcularEstado(comanda, LocalDateTime.now(clock));
        Pedido guardado = pedidoRepository.save(comanda);
        logger.info("Mesa {}: {} producto(s) más en la comanda #{}", comanda.getMesa(), dto.getDetalles().size(), guardado.getId());
        realtimeNotifier.avisarPedidos();
        PedidoResponseDTO respuesta = toDTO(guardado);
        respuesta.setAgregadoAMesaAbierta(true);
        return respuesta;
    }

    /** Cada producto es una tarjeta nueva, aunque ya haya uno igual en la mesa. */
    private void agregarTarjetas(Pedido pedido, List<DetallePedidoRequestDTO> items) {
        String sinCocina = configLocalService.obtener().getCategoriasSinCocina();
        LocalDateTime ahora = LocalDateTime.now(clock);
        for (DetallePedidoRequestDTO item : items) {
            Producto producto = productoActivo(item.getProductoId());
            String variante = normalizarVariante(item.getVariante());
            if (item.getCantidad() != null && item.getCantidad() > CANTIDAD_MAXIMA) {
                throw new BusinessException("La cantidad máxima por producto es " + CANTIDAD_MAXIMA + ".");
            }
            DetallePedido tarjeta = nuevoDetalle(pedido, producto, variante, precioDe(producto, variante), item.getCantidad());
            tarjeta.setEstado(EstadoPedido.PENDIENTE);
            tarjeta.setVaACocina(ComandaMesa.categoriaVaACocina(producto.getCategoria(), sinCocina));
            tarjeta.setCreadoEn(ahora);
            tarjeta.setEstadoEn(ahora);
            pedido.getDetalles().add(tarjeta);
        }
    }

    private Pedido buscarComanda(Long id) {
        Pedido pedido = buscar(id);
        if (!pedido.esComandaPorTarjetas()) {
            throw new BusinessException("Este pedido no es una comanda de mesa por tarjetas.");
        }
        return pedido;
    }

    private static DetallePedido tarjeta(Pedido pedido, Long detalleId) {
        return pedido.getDetalles().stream()
                .filter(d -> d.getId() != null && d.getId().equals(detalleId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Plato de la comanda", detalleId));
    }

    private static String nombreEstado(EstadoPedido estado) {
        return switch (estado) {
            case PENDIENTE -> "nuevo";
            case PREPARACION -> "en preparación";
            case LISTO -> "listo";
            case ENTREGADO -> "entregado";
            case CANCELADO -> "cancelado";
        };
    }

    /** Corrige nombre, teléfono, dirección o mesa. Solo cambia lo que viene con valor. */
    public PedidoResponseDTO actualizarDatosCliente(Long pedidoId, PedidoDatosClienteDTO dto) {
        Pedido pedido = buscarEditable(pedidoId);
        if (dto.getNombreCliente() != null) pedido.setNombreCliente(dto.getNombreCliente());
        if (dto.getTelefonoCliente() != null) pedido.setTelefonoCliente(dto.getTelefonoCliente());
        if (dto.getDireccionEntrega() != null) pedido.setDireccionEntrega(dto.getDireccionEntrega());
        if (dto.getMesa() != null) {
            String mesa = normalizarMesa(dto.getMesa());
            if (pedido.esComandaPorTarjetas() && mesa != null && !mesa.equals(pedido.getMesa())
                    && !pedidoRepository.findComandasAbiertasDeMesa(mesa).isEmpty()) {
                throw new BusinessException("La mesa " + mesa + " ya tiene una comanda abierta.");
            }
            if (pedido.esComandaPorTarjetas() && mesa == null) {
                throw new BusinessException("Falta el número de mesa.");
            }
            if (mesa == null && pedido.getTipo() == TipoPedido.LOCAL
                    && (pedido.getNombreCliente() == null || pedido.getNombreCliente().isBlank())) {
                throw new BusinessException("Falta el número de mesa.");
            }
            pedido.setMesa(mesa);
        }
        pedido.setModificadoEn(LocalDateTime.now(clock));
        return guardarYAvisar(pedido);
    }

    /** Reglas de la configuración que aplican a los pedidos hechos desde la web. */
    private void validarReglasDelLocal(PedidoRequestDTO dto) {
        ConfigLocal cfg = configLocalService.obtener();
        boolean cerrado = "close".equalsIgnoreCase(cfg.getEstadoManual())
                || (!"open".equalsIgnoreCase(cfg.getEstadoManual()) && !HorarioLocal.abiertoEn(cfg, LocalDateTime.now(clock)));
        if (cerrado) {
            throw new BusinessException("El local está cerrado en este momento. Podés ver la carta, pero no hacer pedidos.");
        }
        if (dto.getTipo() == TipoPedido.DELIVERY && Boolean.FALSE.equals(cfg.getAceptaDelivery())) {
            throw new BusinessException("El delivery no está disponible en este momento.");
        }
        if (dto.getTipo() == TipoPedido.RETIRO && Boolean.FALSE.equals(cfg.getAceptaRetiro())) {
            throw new BusinessException("El retiro en el local no está disponible en este momento.");
        }
        MetodoPago pago = dto.getMetodoPagoPreferido();
        if (pago == null) return;
        if (dto.getTipo() == TipoPedido.DELIVERY && pago == MetodoPago.TARJETA) {
            throw new BusinessException("El delivery no se puede pagar con tarjeta. Elegí efectivo o transferencia.");
        }
        String aceptados = cfg.getPagosAceptados();
        if (aceptados != null && !aceptados.isBlank()
                && Arrays.stream(aceptados.split(",")).map(String::trim).noneMatch(pago.name()::equalsIgnoreCase)) {
            throw new BusinessException("Ese medio de pago no está disponible.");
        }
    }

    private void validarIdentificacion(PedidoRequestDTO dto, String mesa) {
        boolean sinNombre = dto.getNombreCliente() == null || dto.getNombreCliente().isBlank();
        if (dto.getTipo() == TipoPedido.LOCAL) {
            if (mesa == null && sinNombre) throw new BusinessException("Falta el número de mesa.");
        } else if (sinNombre) {
            throw new BusinessException("El nombre del cliente es obligatorio");
        }
        if (dto.getTipo() == TipoPedido.DELIVERY
                && (dto.getDireccionEntrega() == null || dto.getDireccionEntrega().isBlank())) {
            throw new BusinessException("El delivery requiere una dirección de entrega");
        }
    }

    /** Devuelve la mesa como número sin ceros adelante, o null si vino vacía. */
    static String normalizarMesa(String mesa) {
        if (mesa == null || mesa.isBlank()) return null;
        String limpia = mesa.trim();
        if (!limpia.matches("\\d{1,3}")) {
            throw new BusinessException("El número de mesa tiene que ser un número de 1 a " + MESA_MAXIMA + ".");
        }
        int numero = Integer.parseInt(limpia);
        if (numero < 1) {
            throw new BusinessException("El número de mesa tiene que ser un número de 1 a " + MESA_MAXIMA + ".");
        }
        return String.valueOf(numero);
    }

    private static void validarTransicion(EstadoPedido actual, EstadoPedido nuevo) {
        boolean valido = switch (actual) {
            case PENDIENTE -> nuevo == EstadoPedido.PREPARACION;
            case PREPARACION -> nuevo == EstadoPedido.LISTO;
            case LISTO -> nuevo == EstadoPedido.PREPARACION || nuevo == EstadoPedido.ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };
        if (!valido) {
            throw new BusinessException("No se puede pasar el pedido de " + actual + " a " + nuevo + ".");
        }
    }

    /**
     * Precio según la opción elegida, siempre tomado de la base. Una opción que
     * el producto no tiene se rechaza: así nada inventado llega a la comanda.
     */
    private double precioDe(Producto producto, String variante) {
        if (variante == null) return producto.getPrecio();
        List<Map<String, Object>> variantes = leerVariantes(producto);
        if (!variantes.isEmpty()) {
            return variantes.stream()
                    .filter(v -> variante.equalsIgnoreCase(String.valueOf(v.get("nombre")).trim()))
                    .map(v -> v.get("precio"))
                    .filter(Objects::nonNull)
                    .map(precio -> Double.valueOf(String.valueOf(precio)))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("La opción '" + variante + "' de '" + producto.getNombre()
                            + "' ya no está disponible. Volvé a elegirla desde el menú actualizado."));
        }
        boolean esquemaMediaEntera = producto.getPrecioEntera() != null && producto.getPrecioEntera() > 0;
        if (esquemaMediaEntera && "Entera".equalsIgnoreCase(variante)) return producto.getPrecioEntera();
        if (esquemaMediaEntera && "Media".equalsIgnoreCase(variante)) return producto.getPrecio();
        if (tieneAcompanamiento(producto)) return producto.getPrecio();
        throw new BusinessException("'" + producto.getNombre() + "' no tiene la opción '" + variante
                + "'. Volvé a elegirlo desde el menú actualizado.");
    }

    private List<Map<String, Object>> leerVariantes(Producto producto) {
        String json = producto.getVariantes();
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            logger.warn("Variantes ilegibles en el producto {}: {}", producto.getId(), e.getMessage());
            return List.of();
        }
    }

    private static boolean tieneAcompanamiento(Producto producto) {
        return producto.getDescripcion() != null && producto.getDescripcion().contains("[acomp");
    }

    private static String normalizarVariante(String variante) {
        if (variante == null || variante.isBlank()) return null;
        String limpia = variante.trim();
        if (limpia.length() > MAX_LARGO_OPCION) throw new BusinessException("La opción elegida es demasiado larga.");
        return limpia;
    }

    private List<Pedido> filtrarSegunRol(List<Pedido> pedidos) {
        if (!tieneRol(Rol.MOZO)) return pedidos;
        Usuario yo = usuarioAutenticado();
        if (yo == null) return List.of();
        Set<Long> mozos = idsDeMozos();
        return pedidos.stream().filter(p -> esDelMozo(p, yo, mozos)).toList();
    }

    /**
     * El mozo ve y maneja lo que cargó él y, en las comandas de mesa, también
     * las que abrió el cajero o el admin (no las de otro mozo).
     */
    private static boolean esDelMozo(Pedido pedido, Usuario mozo, Set<Long> idsDeMozos) {
        Long creador = pedido.getCreadoPorUsuarioId();
        if (mozo.getId().equals(creador)) return true;
        return pedido.esComandaPorTarjetas() && creador != null && !idsDeMozos.contains(creador);
    }

    private Set<Long> idsDeMozos() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.MOZO)
                .map(Usuario::getId)
                .collect(Collectors.toSet());
    }

    private List<Pedido> canceladosRecientes(List<Pedido> cancelados) {
        LocalDateTime limite = LocalDateTime.now(clock).minusHours(HORAS_CANCELADOS_VISIBLES_EN_COCINA);
        return cancelados.stream()
                .filter(p -> p.getModificadoEn() != null && p.getModificadoEn().isAfter(limite))
                .toList();
    }

    /** El mozo solo edita los pedidos que cargó él. */
    private void verificarPuedeEditar(Pedido pedido) {
        if (!tieneRol(Rol.MOZO)) return;
        Usuario yo = usuarioAutenticado();
        if (yo == null || !esDelMozo(pedido, yo, idsDeMozos())) {
            throw new BusinessException("Solo podés editar los pedidos de tus propias mesas.");
        }
    }

    private Usuario usuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        return usuarioRepository.findByEmail(auth.getName()).orElse(null);
    }

    private static boolean tieneRol(Rol rol) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + rol.name()).equals(a.getAuthority()));
    }

    private Usuario primerAdminActivo() {
        List<Usuario> activos = usuarioRepository.findAll().stream().filter(Usuario::estaActivo).toList();
        return activos.stream().filter(u -> u.getRol() == Rol.ADMIN).findFirst()
                .or(() -> activos.stream().findFirst())
                .orElseThrow(() -> new BusinessException("No hay usuarios cargados en el sistema"));
    }

    private Pedido buscar(Long id) {
        return pedidoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pedido", id));
    }

    private Pedido buscarEditable(Long id) {
        Pedido pedido = buscar(id);
        verificarPuedeEditar(pedido);
        if (pedido.esComandaPorTarjetas()) {
            if (pedido.estaPagado()) {
                throw new BusinessException("La mesa ya está cobrada. Si piden algo más, cargalo y se abre una cuenta nueva.");
            }
            if (pedido.getEstado() == EstadoPedido.ENTREGADO || pedido.getEstado() == EstadoPedido.CANCELADO) {
                throw new BusinessException("La comanda de esa mesa ya está cerrada.");
            }
            return pedido;
        }
        if (pedido.getEstado() != EstadoPedido.PENDIENTE && pedido.getEstado() != EstadoPedido.PREPARACION) {
            throw new BusinessException("Solo se pueden modificar pedidos pendientes o en preparación.");
        }
        if (pedido.estaPagado()) {
            throw new BusinessException("El pedido ya está cobrado y no se puede modificar. Si piden algo más, cargalo como un pedido nuevo.");
        }
        return pedido;
    }

    private Producto productoActivo(Long id) {
        Producto producto = productoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Producto", id));
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new BusinessException("El producto '" + producto.getNombre() + "' no está disponible");
        }
        return producto;
    }

    private static DetallePedido nuevoDetalle(Pedido pedido, Producto producto, String variante, double precio, int cantidad) {
        return DetallePedido.builder()
                .pedido(pedido)
                .producto(producto)
                .cantidad(cantidad)
                .variante(variante)
                .precioUnitario(precio)
                .costoUnitario(producto.getCosto())
                .subtotal(Dinero.subtotal(precio, cantidad))
                .build();
    }

    private static void actualizarCantidad(DetallePedido detalle, int cantidad) {
        if (cantidad > CANTIDAD_MAXIMA) {
            throw new BusinessException("La cantidad máxima por producto es " + CANTIDAD_MAXIMA + ".");
        }
        detalle.setCantidad(cantidad);
        detalle.setSubtotal(Dinero.subtotal(detalle.getPrecioUnitario(), cantidad));
    }

    private double costoEnvioActual() {
        Integer costo = configLocalService.obtener().getCostoDelivery();
        return costo != null && costo > 0 ? costo : 0.0;
    }

    private static void recalcularTotal(Pedido pedido) {
        double productos = pedido.getDetalles().stream()
                .filter(d -> !d.estaCancelada())
                .mapToDouble(DetallePedido::getSubtotal).sum();
        double envio = pedido.getCostoEnvio() != null ? pedido.getCostoEnvio() : 0.0;
        pedido.setTotal(Dinero.alPesoHaciaArriba(productos + envio));
    }

    /** Guarda cómo estaba el pedido antes de la primera edición, para que cocina vea qué cambió. */
    private void capturarSnapshotSiHaceFalta(Pedido pedido) {
        if (pedido.getDetalleSnapshotAntesEdicion() != null) return;
        List<Map<String, Object>> foto = pedido.getDetalles().stream().map(d -> {
            Map<String, Object> linea = new LinkedHashMap<>();
            linea.put("nombre", d.getProducto() != null ? d.getProducto().getNombre() : null);
            linea.put("categoria", d.getProducto() != null ? d.getProducto().getCategoria() : null);
            linea.put("variante", d.getVariante());
            linea.put("cantidad", d.getCantidad());
            return linea;
        }).toList();
        try {
            pedido.setDetalleSnapshotAntesEdicion(objectMapper.writeValueAsString(foto));
        } catch (Exception e) {
            logger.warn("No se pudo guardar la foto del pedido {} antes de editarlo: {}", pedido.getId(), e.getMessage());
        }
    }

    private PedidoResponseDTO guardarEdicion(Pedido pedido) {
        pedido.setModificadoEn(LocalDateTime.now(clock));
        recalcularTotal(pedido);
        return guardarYAvisar(pedido);
    }

    private PedidoResponseDTO guardarYAvisar(Pedido pedido) {
        PedidoResponseDTO resultado = toDTO(pedidoRepository.save(pedido));
        realtimeNotifier.avisarPedidos();
        return resultado;
    }

    private static String firma(PedidoRequestDTO dto, Usuario autenticado) {
        String items = dto.getDetalles().stream()
                .map(d -> d.getProductoId() + ":" + d.getCantidad() + ":" + Objects.toString(d.getVariante(), ""))
                .sorted()
                .collect(Collectors.joining(","));
        return String.join("|", "PED", autenticado != null ? String.valueOf(autenticado.getId()) : "web",
                String.valueOf(dto.getTipo()),
                Objects.toString(dto.getTelefonoCliente(), "").trim(),
                Objects.toString(dto.getNombreCliente(), "").trim(),
                Objects.toString(dto.getMesa(), "").trim(),
                items);
    }

    private List<PedidoResponseDTO> aDTOs(List<Pedido> pedidos) {
        return pedidos.stream().map(this::toDTO).toList();
    }

    public PedidoResponseDTO toDTO(Pedido p) {
        List<DetallePedidoResponseDTO> detalles = p.getDetalles().stream()
                .map(d -> DetallePedidoResponseDTO.builder()
                        .id(d.getId())
                        .cantidad(d.getCantidad())
                        .precioUnitario(d.getPrecioUnitario())
                        .subtotal(d.getSubtotal())
                        .variante(d.getVariante())
                        .estado(d.getEstado())
                        .vaACocina(d.esTarjeta() ? d.pasaPorCocina() : null)
                        .creadoEn(d.getCreadoEn())
                        .estadoEn(d.getEstadoEn())
                        .modificadoEn(d.getModificadoEn())
                        .producto(d.getProducto() != null ? productoService.toDTO(d.getProducto()) : null)
                        .build())
                .toList();

        boolean activo = p.getEstado() == EstadoPedido.PENDIENTE
                || p.getEstado() == EstadoPedido.PREPARACION
                || p.getEstado() == EstadoPedido.LISTO;

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
                .pagado(p.estaPagado())
                .metodoPagoCobrado(p.getVenta() != null ? p.getVenta().getMetodoPago() : null)
                .cliente(p.getCliente() != null ? clienteService.toDTO(p.getCliente()) : null)
                .detalles(detalles)
                .porTarjetas(p.esComandaPorTarjetas())
                .cancelable(activo && !p.estaPagado())
                .build();
    }
}
