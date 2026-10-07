package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.ReservaRequestDTO;
import com.gestorgastronomico.dto.ReservaResponseDTO;
import com.gestorgastronomico.entity.ConfigLocal;
import com.gestorgastronomico.entity.Reserva;
import com.gestorgastronomico.entity.Reserva.EstadoReserva;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.repository.ClienteRepository;
import com.gestorgastronomico.repository.ReservaRepository;
import com.gestorgastronomico.websocket.RealtimeNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservaServiceTest {

    // Martes 6 de octubre de 2026, 10:00 en Argentina.
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-06T13:00:00Z"), ZONA);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 11);

    @Mock private ReservaRepository reservaRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ClienteService clienteService;
    @Mock private ConfigLocalService configLocalService;
    @Mock private RealtimeNotifier realtimeNotifier;

    private ReservaService reservaService;
    private ConfigLocal config;

    @BeforeEach
    void setUp() {
        reservaService = new ReservaService(reservaRepository, clienteRepository, clienteService,
                configLocalService, realtimeNotifier, new EnvioDuplicado(RELOJ), RELOJ);
        // Lunes a viernes, 11 a 15 y 20 a 01.
        config = ConfigLocal.builder().id(1L).dias("1,2,3,4,5")
                .mDesde("11:00").mHasta("15:00").nDesde("20:00").nHasta("01:00").build();
        when(configLocalService.obtener()).thenReturn(config);
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservaRepository.findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(any(), any(), eq(EstadoReserva.CONFIRMADA)))
                .thenReturn(List.of());
    }

    @Test
    void reservasDeHoy_incluyenLaMadrugadaDelTurnoDeEstaNoche() {
        LocalDate hoy = LocalDate.of(2026, 10, 6);
        LocalDate manana = hoy.plusDays(1);
        Reserva cena = Reserva.builder().id(1L).nombreCliente("Ana").fecha(hoy).hora(LocalTime.of(21, 0))
                .cantidadPersonas(2).estado(EstadoReserva.CONFIRMADA).build();
        Reserva madrugada = Reserva.builder().id(2L).nombreCliente("Beto").fecha(manana).hora(LocalTime.of(0, 30))
                .cantidadPersonas(2).estado(EstadoReserva.CONFIRMADA).build();
        Reserva cenaDeManana = Reserva.builder().id(3L).nombreCliente("Caro").fecha(manana).hora(LocalTime.of(21, 0))
                .cantidadPersonas(2).estado(EstadoReserva.CONFIRMADA).build();
        when(reservaRepository.findByFechaOrderByHoraAsc(hoy)).thenReturn(List.of(cena));
        when(reservaRepository.findByFechaOrderByHoraAsc(manana)).thenReturn(List.of(madrugada, cenaDeManana));

        List<ReservaResponseDTO> deHoy = reservaService.listarDeHoy();

        assertEquals(List.of(1L, 2L), deHoy.stream().map(ReservaResponseDTO::getId).toList());
    }

    @Test
    void reservaDentroDelHorario_seConfirma() {
        ReservaResponseDTO reserva = reservaService.crear(pedido("Ana", MIERCOLES, "21:00", 4));

        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
    }

    @Test
    void reservaDespuesDeMedianoche_cuentaComoTurnoDeLaNoche() {
        ReservaResponseDTO reserva = reservaService.crear(pedido("Ana", MIERCOLES, "00:30", 2));

        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
    }

    @Test
    void reservaConElLocalCerrado_seRechaza() {
        assertThrows(BusinessException.class, () -> reservaService.crear(pedido("Ana", DOMINGO, "21:00", 2)));
        assertThrows(BusinessException.class, () -> reservaService.crear(pedido("Ana", MIERCOLES, "03:00", 2)));
        assertThrows(BusinessException.class, () -> reservaService.crear(pedido("Ana", MIERCOLES, "17:00", 2)));
    }

    @Test
    void reservaEnElPasado_seRechaza() {
        assertThrows(BusinessException.class,
                () -> reservaService.crear(pedido("Ana", LocalDate.of(2026, 10, 5), "21:00", 2)));
    }

    @Test
    void masPersonasQueElMaximoPorReserva_seRechaza() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> reservaService.crear(pedido("Ana", MIERCOLES, "21:00", 25)));

        assertTrue(error.getMessage().contains("20"));
    }

    @Test
    void elMaximoPorReserva_esConfigurable() {
        config.setMaxPersonasReserva(30);

        ReservaResponseDTO reserva = reservaService.crear(pedido("Ana", MIERCOLES, "21:00", 25));

        assertEquals(25, reserva.getCantidadPersonas());
    }

    @Test
    void turnoLleno_rechazaYDiceCuantosLugaresQuedan() {
        config.setMaxPersonasTurno(10);
        Reserva existente = Reserva.builder().id(1L).fecha(MIERCOLES).hora(LocalTime.of(22, 0))
                .cantidadPersonas(8).estado(EstadoReserva.CONFIRMADA).build();
        when(reservaRepository.findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(any(), any(), eq(EstadoReserva.CONFIRMADA)))
                .thenReturn(List.of(existente));

        BusinessException error = assertThrows(BusinessException.class,
                () -> reservaService.crear(pedido("Ana", MIERCOLES, "21:00", 3)));

        assertTrue(error.getMessage().contains("2 personas"));
    }

    @Test
    void reservasDeOtroTurno_noOcupanLugar() {
        config.setMaxPersonasTurno(10);
        Reserva mediodia = Reserva.builder().id(1L).fecha(MIERCOLES).hora(LocalTime.of(12, 0))
                .cantidadPersonas(10).estado(EstadoReserva.CONFIRMADA).build();
        when(reservaRepository.findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(any(), any(), eq(EstadoReserva.CONFIRMADA)))
                .thenReturn(List.of(mediodia));

        ReservaResponseDTO reserva = reservaService.crear(pedido("Ana", MIERCOLES, "21:00", 4));

        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
    }

    @Test
    void clienteQueNoVino_quedaCanceladaYMarcada() {
        Reserva reserva = Reserva.builder().id(3L).nombreCliente("Ana").fecha(MIERCOLES)
                .hora(LocalTime.of(21, 0)).cantidadPersonas(2).estado(EstadoReserva.CONFIRMADA).build();
        when(reservaRepository.findById(3L)).thenReturn(Optional.of(reserva));

        ReservaResponseDTO resultado = reservaService.marcarNoAsistio(3L);

        assertEquals(EstadoReserva.CANCELADA, resultado.getEstado());
        assertTrue(resultado.isNoAsistio());
    }

    private static ReservaRequestDTO pedido(String nombre, LocalDate fecha, String hora, int personas) {
        ReservaRequestDTO dto = new ReservaRequestDTO();
        dto.setNombreCliente(nombre);
        dto.setFecha(fecha);
        dto.setHora(LocalTime.parse(hora));
        dto.setCantidadPersonas(personas);
        dto.setTelefono("3447" + personas + hora.replace(":", ""));
        return dto;
    }
}
