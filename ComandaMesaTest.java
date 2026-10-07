package com.gestorgastronomico.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestorgastronomico.dto.DetallePedidoRequestDTO;
import com.gestorgastronomico.dto.DetallePedidoResponseDTO;
import com.gestorgastronomico.dto.PedidoRequestDTO;
import com.gestorgastronomico.dto.PedidoResponseDTO;
import com.gestorgastronomico.entity.*;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.repository.ClienteRepository;
import com.gestorgastronomico.repository.PedidoRepository;
import com.gestorgastronomico.repository.ProductoRepository;
import com.gestorgastronomico.repository.UsuarioRepository;
import com.gestorgastronomico.websocket.RealtimeNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Comanda de mesa por tarjetas: una mesa, una comanda; cada plato con su estado. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComandaMesaTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-07T23:00:00Z"), ZONA);

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProductoService productoService;
    @Mock private ConfigLocalService configLocalService;
    @Mock private ClienteService clienteService;
    @Mock private RealtimeNotifier realtimeNotifier;

    private PedidoService pedidoService;
    private Usuario mozo;
    private Usuario otroMozo;
    private Usuario cajero;
    private Usuario cocina;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        pedidoService = new PedidoService(pedidoRepository, productoRepository, clienteRepository,
                usuarioRepository, productoService, configLocalService, clienteService, realtimeNotifier,
                new ObjectMapper(), new EnvioDuplicado(RELOJ), RELOJ);

        Usuario admin = Usuario.builder().id(1L).nombre("Admin").email("admin@test.com").rol(Rol.ADMIN).activo(true).build();
        mozo = Usuario.builder().id(4L).nombre("Juan").email("juan@test.com").rol(Rol.MOZO).activo(true).build();
        otroMozo = Usuario.builder().id(5L).nombre("Mateo").email("mateo@test.com").rol(Rol.MOZO).activo(true).build();
        cajero = Usuario.builder().id(2L).nombre("Caja").email("caja@test.com").rol(Rol.CAJERO).activo(true).build();
        cocina = Usuario.builder().id(3L).nombre("Cocina").email("cocina@test.com").rol(Rol.COCINA).activo(true).build();
        for (Usuario u : List.of(admin, mozo, otroMozo, cajero, cocina)) {
            when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(usuarioRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(usuarioRepository.findAll()).thenReturn(List.of(admin, mozo, otroMozo, cajero, cocina));

        producto(10L, "Empanadas JyQ", "Entradas", 6000.0);
        producto(11L, "Pizza Muzzarella", "Pizzas", 9000.0);
        producto(12L, "Coca-Cola 500ml", "Bebidas", 2500.0);
        producto(13L, "Flan", "Postres", 3000.0);

        when(configLocalService.obtener()).thenReturn(ConfigLocal.builder().id(1L).estadoManual("auto").build());
        when(pedidoRepository.findComandasAbiertasDeMesa(anyString())).thenReturn(List.of());
        // Simula la base: le da id a los platos nuevos.
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) p.setId(ids.incrementAndGet());
            p.getDetalles().forEach(d -> { if (d.getId() == null) d.setId(ids.incrementAndGet()); });
            when(pedidoRepository.findById(p.getId())).thenReturn(Optional.of(p));
            return p;
        });
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mozoAbreMesa_cadaProductoEsUnaTarjetaNueva_yLasBebidasNoVanACocina() {
        como(mozo);

        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 1), item(11L, 1), item(12L, 2)));

        assertTrue(mesa.isPorTarjetas());
        assertFalse(mesa.isAgregadoAMesaAbierta());
        assertEquals(3, mesa.getDetalles().size());
        assertTrue(mesa.getDetalles().stream().allMatch(d -> d.getEstado() == EstadoPedido.PENDIENTE));
        assertEquals(Boolean.FALSE, tarjetaDe(mesa, "Coca-Cola 500ml").getVaACocina());
        assertEquals(Boolean.TRUE, tarjetaDe(mesa, "Pizza Muzzarella").getVaACocina());
        assertEquals(20000.0, mesa.getTotal(), 0.001);
        assertEquals(EstadoPedido.PENDIENTE, mesa.getEstado());
    }

    @Test
    void loQueSeCargaDespues_seSumaALaMismaComanda() {
        como(mozo);
        PedidoResponseDTO primera = pedidoService.crear(pedidoMesa("5", item(10L, 1)));
        Pedido comanda = pedidoRepository.findById(primera.getId()).orElseThrow();
        when(pedidoRepository.findComandasAbiertasDeMesa("5")).thenReturn(List.of(comanda));

        PedidoResponseDTO despues = pedidoService.crear(pedidoMesa("05", item(13L, 1), item(10L, 1)));

        assertEquals(primera.getId(), despues.getId());
        assertTrue(despues.isAgregadoAMesaAbierta());
        assertEquals(3, despues.getDetalles().size(), "las empanadas repetidas son otra tarjeta");
        assertEquals(15000.0, despues.getTotal(), 0.001);
    }

    @Test
    void mesaDeOtroMozo_noSeMezcla() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 1)));
        Pedido comanda = pedidoRepository.findById(mesa.getId()).orElseThrow();
        when(pedidoRepository.findComandasAbiertasDeMesa("5")).thenReturn(List.of(comanda));

        como(otroMozo);
        BusinessException error = assertThrows(BusinessException.class,
                () -> pedidoService.crear(pedidoMesa("5", item(13L, 1))));
        assertTrue(error.getMessage().contains("La mesa 5 la atiende Juan"));

        como(cajero);
        assertTrue(pedidoService.crear(pedidoMesa("5", item(13L, 2))).isAgregadoAMesaAbierta());
    }

    @Test
    void recorridoDeUnPlato_cocinaLoHaceYElMozoLoEntrega() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(11L, 1)));
        Long pizza = mesa.getDetalles().get(0).getId();

        como(cocina);
        pedidoService.cambiarEstadoTarjeta(mesa.getId(), pizza, EstadoPedido.PREPARACION);
        PedidoResponseDTO lista = pedidoService.cambiarEstadoTarjeta(mesa.getId(), pizza, EstadoPedido.LISTO);
        assertEquals(EstadoPedido.LISTO, lista.getEstado());
        assertThrows(BusinessException.class,
                () -> pedidoService.cambiarEstadoTarjeta(mesa.getId(), pizza, EstadoPedido.ENTREGADO));

        como(mozo);
        PedidoResponseDTO entregada = pedidoService.cambiarEstadoTarjeta(mesa.getId(), pizza, EstadoPedido.ENTREGADO);
        assertEquals(EstadoPedido.ENTREGADO, entregada.getDetalles().get(0).getEstado());
        assertEquals(EstadoPedido.LISTO, entregada.getEstado(), "sigue abierta hasta cobrarla");
    }

    @Test
    void mozo_noCambiaEstadosDeCocina() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(11L, 1)));

        assertThrows(BusinessException.class, () -> pedidoService.cambiarEstadoTarjeta(
                mesa.getId(), mesa.getDetalles().get(0).getId(), EstadoPedido.PREPARACION));
    }

    @Test
    void bebida_laEntregaElMozoSinPasarPorCocina() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(12L, 2)));
        Long coca = mesa.getDetalles().get(0).getId();
        assertEquals(EstadoPedido.LISTO, mesa.getEstado(), "cocina no tiene nada que hacer");

        assertThrows(BusinessException.class,
                () -> pedidoService.cambiarEstadoTarjeta(mesa.getId(), coca, EstadoPedido.PREPARACION));
        PedidoResponseDTO entregada = pedidoService.cambiarEstadoTarjeta(mesa.getId(), coca, EstadoPedido.ENTREGADO);

        assertEquals(EstadoPedido.ENTREGADO, entregada.getDetalles().get(0).getEstado());
    }

    @Test
    void platoCancelado_quedaTachadoYNoSuma() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 1), item(13L, 1)));
        Long flan = tarjetaDe(mesa, "Flan").getId();

        PedidoResponseDTO sinFlan = pedidoService.cancelarTarjeta(mesa.getId(), flan);

        assertEquals(2, sinFlan.getDetalles().size(), "el plato no se borra");
        assertEquals(EstadoPedido.CANCELADO, tarjetaDe(sinFlan, "Flan").getEstado());
        assertEquals(6000.0, sinFlan.getTotal(), 0.001);
    }

    @Test
    void sacarTodosLosPlatos_cancelaLaComanda() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 1)));

        PedidoResponseDTO vacia = pedidoService.cancelarTarjeta(mesa.getId(), mesa.getDetalles().get(0).getId());

        assertEquals(EstadoPedido.CANCELADO, vacia.getEstado());
    }

    @Test
    void platoEntregado_soloLoSacaElCajero() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(12L, 1), item(10L, 1)));
        Long coca = tarjetaDe(mesa, "Coca-Cola 500ml").getId();
        pedidoService.cambiarEstadoTarjeta(mesa.getId(), coca, EstadoPedido.ENTREGADO);

        assertThrows(BusinessException.class, () -> pedidoService.cancelarTarjeta(mesa.getId(), coca));

        como(cajero);
        assertEquals(EstadoPedido.CANCELADO,
                tarjetaDe(pedidoService.cancelarTarjeta(mesa.getId(), coca), "Coca-Cola 500ml").getEstado());
    }

    @Test
    void mozoYCocina_cancelanLaComandaEntera_peroNoUnDelivery() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 1)));

        como(cocina);
        assertEquals(EstadoPedido.CANCELADO, pedidoService.cancelar(mesa.getId()).getEstado());

        Pedido delivery = Pedido.builder().id(900L).tipo(TipoPedido.DELIVERY).estado(EstadoPedido.PENDIENTE)
                .detalles(new ArrayList<>()).build();
        when(pedidoRepository.findById(900L)).thenReturn(Optional.of(delivery));
        assertThrows(BusinessException.class, () -> pedidoService.cancelar(900L));
    }

    @Test
    void mesaCobrada_noSeLeSacanPlatos() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 1)));
        Pedido comanda = pedidoRepository.findById(mesa.getId()).orElseThrow();
        comanda.setVenta(Venta.builder().id(1L).metodoPago(MetodoPago.EFECTIVO).build());

        assertThrows(BusinessException.class,
                () -> pedidoService.cancelarTarjeta(mesa.getId(), mesa.getDetalles().get(0).getId()));
    }

    @Test
    void cobradaConElPostrePendiente_seCierraCuandoSeEntregaElUltimo() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(12L, 1)));
        Pedido comanda = pedidoRepository.findById(mesa.getId()).orElseThrow();
        comanda.setVenta(Venta.builder().id(1L).metodoPago(MetodoPago.EFECTIVO).build());
        ComandaMesa.recalcularEstado(comanda, LocalDateTime.now(RELOJ));
        assertEquals(EstadoPedido.LISTO, comanda.getEstado());

        PedidoResponseDTO cerrada = pedidoService.cambiarEstadoTarjeta(
                mesa.getId(), mesa.getDetalles().get(0).getId(), EstadoPedido.ENTREGADO);

        assertEquals(EstadoPedido.ENTREGADO, cerrada.getEstado());
    }

    @Test
    void mesaAbiertaPorElCajero_elMozoPuedeSumarle() {
        como(cajero);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("7", item(10L, 1)));
        Pedido comanda = pedidoRepository.findById(mesa.getId()).orElseThrow();
        when(pedidoRepository.findComandasAbiertasDeMesa("7")).thenReturn(List.of(comanda));

        como(mozo);
        assertTrue(pedidoService.crear(pedidoMesa("7", item(13L, 1))).isAgregadoAMesaAbierta());
    }

    @Test
    void mesaConPlatosEntregados_laCancelaSoloElCajero() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(12L, 1), item(10L, 1)));
        pedidoService.cambiarEstadoTarjeta(mesa.getId(), tarjetaDe(mesa, "Coca-Cola 500ml").getId(), EstadoPedido.ENTREGADO);

        assertThrows(BusinessException.class, () -> pedidoService.cancelar(mesa.getId()));
        como(cajero);
        assertEquals(EstadoPedido.CANCELADO, pedidoService.cancelar(mesa.getId()).getEstado());
    }

    @Test
    void mesaCobrada_elCajeroCierraLoQueQuedoSinEntregar() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(13L, 1)));
        Pedido comanda = pedidoRepository.findById(mesa.getId()).orElseThrow();
        comanda.setVenta(Venta.builder().id(1L).metodoPago(MetodoPago.EFECTIVO).build());
        Long flan = mesa.getDetalles().get(0).getId();

        assertThrows(BusinessException.class, () -> pedidoService.cambiarEstadoTarjeta(mesa.getId(), flan, EstadoPedido.ENTREGADO));
        como(cajero);
        assertEquals(EstadoPedido.ENTREGADO, pedidoService.cambiarEstadoTarjeta(mesa.getId(), flan, EstadoPedido.ENTREGADO).getEstado());
    }

    @Test
    void cambiarCantidad_marcaElPlatoComoModificado() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 2)));

        PedidoResponseDTO editada = pedidoService.cambiarCantidadDetalle(mesa.getId(), mesa.getDetalles().get(0).getId(), 3);

        assertEquals(3, editada.getDetalles().get(0).getCantidad());
        assertNotNull(editada.getDetalles().get(0).getModificadoEn());
        assertEquals(18000.0, editada.getTotal(), 0.001);
    }

    @Test
    void comandaPorTarjetas_noAvanzaEntera() {
        como(mozo);
        PedidoResponseDTO mesa = pedidoService.crear(pedidoMesa("5", item(10L, 1)));

        como(cajero);
        assertThrows(BusinessException.class, () -> pedidoService.cambiarEstado(mesa.getId(), EstadoPedido.PREPARACION));
    }

    @Test
    void retiroYDelivery_siguenComoAntes() {
        como(cajero);
        PedidoRequestDTO dto = pedidoMesa(null, item(10L, 1));
        dto.setTipo(TipoPedido.RETIRO);
        dto.setNombreCliente("Ana");

        PedidoResponseDTO retiro = pedidoService.crear(dto);

        assertFalse(retiro.isPorTarjetas());
        assertNull(retiro.getDetalles().get(0).getEstado());
    }

    @Test
    void queCategoriaVaACocina() {
        assertFalse(ComandaMesa.categoriaVaACocina("Bebidas", null));
        assertFalse(ComandaMesa.categoriaVaACocina("Cervezas artesanales", null));
        assertTrue(ComandaMesa.categoriaVaACocina("Pizzas", null));
        // Con la lista configurada manda la lista, sin importar tildes ni mayúsculas.
        assertTrue(ComandaMesa.categoriaVaACocina("Bebidas", ""));
        assertFalse(ComandaMesa.categoriaVaACocina("Cafetería", "cafeteria, Tragos"));
        assertTrue(ComandaMesa.categoriaVaACocina("Postres", "Cafetería,Tragos"));
    }

    private void como(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))));
    }

    private void producto(Long id, String nombre, String categoria, double precio) {
        Producto p = Producto.builder().id(id).nombre(nombre).categoria(categoria).precio(precio).activo(true).build();
        when(productoRepository.findById(id)).thenReturn(Optional.of(p));
        when(productoService.toDTO(p)).thenReturn(com.gestorgastronomico.dto.ProductoResponseDTO.builder()
                .id(id).nombre(nombre).categoria(categoria).precio(precio).build());
    }

    private static DetallePedidoResponseDTO tarjetaDe(PedidoResponseDTO pedido, String nombre) {
        return pedido.getDetalles().stream()
                .filter(d -> d.getProducto() != null && nombre.equals(d.getProducto().getNombre()))
                .findFirst().orElseThrow();
    }

    private static DetallePedidoRequestDTO item(Long productoId, int cantidad) {
        DetallePedidoRequestDTO item = new DetallePedidoRequestDTO();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        return item;
    }

    private static PedidoRequestDTO pedidoMesa(String mesa, DetallePedidoRequestDTO... items) {
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setTipo(TipoPedido.LOCAL);
        dto.setMesa(mesa);
        dto.setDetalles(List.of(items));
        return dto;
    }
}
