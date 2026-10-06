package com.gestorgastronomico.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestorgastronomico.dto.DetallePedidoRequestDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-06T15:00:00Z"), ZONA);

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProductoService productoService;
    @Mock private ConfigLocalService configLocalService;
    @Mock private ClienteService clienteService;
    @Mock private RealtimeNotifier realtimeNotifier;

    private PedidoService pedidoService;
    private Usuario admin;
    private Producto plato;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        pedidoService = new PedidoService(pedidoRepository, productoRepository, clienteRepository,
                usuarioRepository, productoService, configLocalService, clienteService, realtimeNotifier,
                new ObjectMapper(), new EnvioDuplicado(RELOJ), RELOJ);

        admin = Usuario.builder().id(1L).nombre("Admin").email("admin@test.com").rol(Rol.ADMIN).activo(true).build();
        Usuario mozo = Usuario.builder().id(4L).nombre("Mozo").email("mozo@test.com").rol(Rol.MOZO).activo(true).build();
        plato = Producto.builder().id(10L).nombre("Milanesa").precio(1000.0).costo(400.0).activo(true).categoria("Platos").build();

        when(usuarioRepository.findAll()).thenReturn(List.of(admin, mozo));
        when(productoRepository.findById(10L)).thenReturn(Optional.of(plato));
        when(configLocalService.obtener()).thenReturn(config("auto", 0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pedidoPublico_calculaPrecioYEnvioEnElServidor() {
        when(configLocalService.obtener()).thenReturn(config("auto", 300));

        PedidoResponseDTO pedido = pedidoService.crear(pedidoPublico(TipoPedido.DELIVERY, null, 1));

        assertEquals(1300.0, pedido.getTotal(), 0.001);
        assertEquals(300.0, pedido.getCostoEnvio(), 0.001);
    }

    @Test
    void envioNegativoEnLaConfiguracion_noSeDescuentaDelTotal() {
        when(configLocalService.obtener()).thenReturn(config("auto", -800));

        PedidoResponseDTO pedido = pedidoService.crear(pedidoPublico(TipoPedido.DELIVERY, null, 1));

        assertEquals(1000.0, pedido.getTotal(), 0.001);
        assertEquals(0.0, pedido.getCostoEnvio(), 0.001);
    }

    @Test
    void preciosConCentavos_seRedondeanAlPesoHaciaArriba() {
        plato.setPrecio(1499.99);

        PedidoResponseDTO uno = pedidoService.crear(pedidoPublico(TipoPedido.RETIRO, null, 1));
        PedidoResponseDTO dos = pedidoService.crear(pedidoPublico(TipoPedido.RETIRO, null, 2));

        assertEquals(1500.0, uno.getTotal(), 0.001);
        assertEquals(3000.0, dos.getTotal(), 0.001);
    }

    @Test
    void editarDelivery_conservaElCostoDeEnvioEnElTotal() {
        pedidoGuardado(5L, EstadoPedido.PENDIENTE, TipoPedido.DELIVERY, 500.0);
        DetallePedidoRequestDTO otro = item(10L, 1, null);

        PedidoResponseDTO resultado = pedidoService.agregarDetalle(5L, otro);

        assertEquals(2500.0, resultado.getTotal(), 0.001);
    }

    @Test
    void pedidoCobrado_noSePuedeEditarNiCancelar() {
        Pedido pedido = pedidoGuardado(7L, EstadoPedido.PREPARACION, TipoPedido.RETIRO, 0.0);
        pedido.setVenta(Venta.builder().id(1L).metodoPago(MetodoPago.EFECTIVO).build());

        assertThrows(BusinessException.class, () -> pedidoService.agregarDetalle(7L, item(10L, 1, null)));
        assertThrows(BusinessException.class, () -> pedidoService.cancelar(7L));
    }

    @Test
    void cancelarPorCambioDeEstado_seRechaza() {
        Pedido pedido = pedidoGuardado(6L, EstadoPedido.PENDIENTE, TipoPedido.RETIRO, 0.0);

        assertThrows(BusinessException.class, () -> pedidoService.cambiarEstado(6L, EstadoPedido.CANCELADO));
        assertEquals(EstadoPedido.PENDIENTE, pedido.getEstado());
    }

    @Test
    void entregarSinCobrar_seRechaza() {
        pedidoGuardado(8L, EstadoPedido.LISTO, TipoPedido.RETIRO, 0.0);

        assertThrows(BusinessException.class, () -> pedidoService.cambiarEstado(8L, EstadoPedido.ENTREGADO));
    }

    @Test
    void pedidoListo_puedeVolverAPreparacion() {
        Pedido pedido = pedidoGuardado(9L, EstadoPedido.LISTO, TipoPedido.RETIRO, 0.0);

        pedidoService.cambiarEstado(9L, EstadoPedido.PREPARACION);

        assertEquals(EstadoPedido.PREPARACION, pedido.getEstado());
    }

    @Test
    void opcionInventada_seRechaza() {
        PedidoRequestDTO dto = pedidoPublico(TipoPedido.RETIRO, "Gigante gratis", 1);

        assertThrows(BusinessException.class, () -> pedidoService.crear(dto));
    }

    @Test
    void localCerrado_rechazaPedidoDeLaWeb() {
        when(configLocalService.obtener()).thenReturn(config("close", 0));

        assertThrows(BusinessException.class, () -> pedidoService.crear(pedidoPublico(TipoPedido.RETIRO, null, 1)));
    }

    @Test
    void deliveryConTarjetaDesdeLaWeb_seRechaza() {
        PedidoRequestDTO dto = pedidoPublico(TipoPedido.DELIVERY, null, 1);
        dto.setMetodoPagoPreferido(MetodoPago.TARJETA);

        assertThrows(BusinessException.class, () -> pedidoService.crear(dto));
    }

    @Test
    void pedidoDeLaWeb_quedaAlPrimerAdminAunqueMandenOtroUsuario() {
        PedidoRequestDTO dto = pedidoPublico(TipoPedido.DELIVERY, null, 1);
        dto.setUsuarioId(4L);

        pedidoService.crear(dto);

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository, atLeastOnce()).save(captor.capture());
        assertEquals(admin.getId(), captor.getValue().getUsuario().getId());
        assertNull(captor.getValue().getCreadoPorUsuarioId());
    }

    @Test
    void pedidoWebFueraDeHorario_seRechaza() {
        ConfigLocal soloNoche = config("auto", 0).toBuilder().dias("1,2,3,4,5").nDesde("20:00").nHasta("23:30").build();
        when(configLocalService.obtener()).thenReturn(soloNoche);

        BusinessException error = assertThrows(BusinessException.class,
                () -> pedidoService.crear(pedidoPublico(TipoPedido.RETIRO, null, 1)));

        assertTrue(error.getMessage().contains("cerrado"));
    }

    @Test
    void localAbiertoAMano_aceptaPedidosFueraDeHorario() {
        ConfigLocal soloNoche = config("open", 0).toBuilder().dias("1,2,3,4,5").nDesde("20:00").nHasta("23:30").build();
        when(configLocalService.obtener()).thenReturn(soloNoche);

        PedidoResponseDTO pedido = pedidoService.crear(pedidoPublico(TipoPedido.RETIRO, null, 1));

        assertEquals(1000.0, pedido.getTotal(), 0.001);
    }

    @Test
    void numeroDeMesa_aceptaDe1a999() {
        assertEquals("5", PedidoService.normalizarMesa("05"));
        assertEquals("999", PedidoService.normalizarMesa(" 999 "));
        assertNull(PedidoService.normalizarMesa(""));
        assertThrows(BusinessException.class, () -> PedidoService.normalizarMesa("-3"));
        assertThrows(BusinessException.class, () -> PedidoService.normalizarMesa("0"));
        assertThrows(BusinessException.class, () -> PedidoService.normalizarMesa("1000"));
        assertThrows(BusinessException.class, () -> PedidoService.normalizarMesa("mesa 5"));
    }

    private static ConfigLocal config(String estado, int envio) {
        return ConfigLocal.builder().id(1L).estadoManual(estado).costoDelivery(envio).build();
    }

    private Pedido pedidoGuardado(Long id, EstadoPedido estado, TipoPedido tipo, double envio) {
        Pedido pedido = Pedido.builder()
                .id(id).fecha(LocalDate.now(RELOJ)).hora(LocalTime.now(RELOJ))
                .estado(estado).tipo(tipo).usuario(admin).costoEnvio(envio)
                .detalles(new ArrayList<>())
                .build();
        pedido.getDetalles().add(DetallePedido.builder().id(1L).pedido(pedido).producto(plato)
                .cantidad(1).precioUnitario(1000.0).subtotal(1000.0).build());
        pedido.setTotal(1000.0 + envio);
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private static DetallePedidoRequestDTO item(Long productoId, int cantidad, String variante) {
        DetallePedidoRequestDTO item = new DetallePedidoRequestDTO();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        item.setVariante(variante);
        return item;
    }

    private static PedidoRequestDTO pedidoPublico(TipoPedido tipo, String variante, int cantidad) {
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setTipo(tipo);
        dto.setNombreCliente("Cliente");
        // Teléfono distinto en cada pedido para que no lo tome como envío repetido.
        dto.setTelefonoCliente(UUID.randomUUID().toString().substring(0, 12));
        dto.setDireccionEntrega("Calle 123");
        dto.setDetalles(List.of(item(10L, cantidad, variante)));
        return dto;
    }
}
