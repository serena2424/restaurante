package com.gestorgastronomico.service;

import com.gestorgastronomico.dto.ReservaRequestDTO;
import com.gestorgastronomico.dto.ReservaResponseDTO;
import com.gestorgastronomico.entity.Cliente;
import com.gestorgastronomico.entity.ConfigLocal;
import com.gestorgastronomico.entity.Reserva;
import com.gestorgastronomico.entity.Reserva.EstadoReserva;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.exception.ResourceNotFoundException;
import com.gestorgastronomico.repository.ClienteRepository;
import com.gestorgastronomico.repository.ReservaRepository;
import com.gestorgastronomico.websocket.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final ConfigLocalService configLocalService;
    private final RealtimeNotifier realtimeNotifier;
    private final EnvioDuplicado envioDuplicado;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarTodas() {
        return reservaRepository.findAll().stream().map(this::toDTO).toList();
    }

    /** Reservas de hoy, incluidas las canceladas, ordenadas por hora. */
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarDeHoy() {
        return listarPorFecha(LocalDate.now(clock));
    }

    /** Reservas confirmadas desde hoy hasta dentro de N días (avisos y recordatorios). */
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarProximas(int dias) {
        LocalDate desde = LocalDate.now(clock);
        LocalDate hasta = desde.plusDays(Math.max(dias, 0));
        return reservaRepository
                .findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(desde, hasta, EstadoReserva.CONFIRMADA)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarPorFecha(LocalDate fecha) {
        return reservaRepository.findByFechaOrderByHoraAsc(fecha).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerPorId(Long id) {
        return toDTO(buscar(id));
    }

    public ReservaResponseDTO crear(ReservaRequestDTO dto) {
        validar(dto, null);

        String firma = String.join("|", "RES", texto(dto.getTelefono()), String.valueOf(dto.getFecha()),
                String.valueOf(dto.getHora()), texto(dto.getNombreCliente()));
        if (envioDuplicado.esRepetido(firma)) {
            throw new BusinessException("Ya recibimos esta reserva hace unos segundos. Esperá un momento antes de reenviarla.");
        }

        Reserva reserva = Reserva.builder()
                .nombreCliente(dto.getNombreCliente().trim())
                .fecha(dto.getFecha())
                .hora(dto.getHora())
                .cantidadPersonas(dto.getCantidadPersonas())
                .telefono(dto.getTelefono())
                .aclaraciones(dto.getAclaraciones())
                .estado(EstadoReserva.CONFIRMADA)
                .build();

        if (dto.getClienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.getClienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.getClienteId()));
            reserva.setCliente(cliente);
        }
        return guardarYAvisar(reserva);
    }

    public ReservaResponseDTO actualizar(Long id, ReservaRequestDTO dto) {
        Reserva reserva = buscar(id);
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new BusinessException("Solo se pueden editar reservas confirmadas.");
        }
        validar(dto, id);
        reserva.setNombreCliente(dto.getNombreCliente().trim());
        reserva.setFecha(dto.getFecha());
        reserva.setHora(dto.getHora());
        reserva.setCantidadPersonas(dto.getCantidadPersonas());
        reserva.setTelefono(dto.getTelefono());
        reserva.setAclaraciones(dto.getAclaraciones());
        return guardarYAvisar(reserva);
    }

    public ReservaResponseDTO cancelar(Long id) {
        Reserva reserva = buscar(id);
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new BusinessException("La reserva ya está cancelada");
        }
        if (reserva.getEstado() == EstadoReserva.COMPLETADA) {
            throw new BusinessException("No se puede cancelar una reserva de un cliente que ya llegó");
        }
        reserva.setEstado(EstadoReserva.CANCELADA);
        return guardarYAvisar(reserva);
    }

    /** El cliente llegó. */
    public ReservaResponseDTO completar(Long id) {
        Reserva reserva = buscar(id);
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new BusinessException("Solo se puede marcar la llegada de una reserva confirmada");
        }
        reserva.setEstado(EstadoReserva.COMPLETADA);
        return guardarYAvisar(reserva);
    }

    /** El cliente no vino: la reserva se cancela y queda marcada para saber por qué. */
    public ReservaResponseDTO marcarNoAsistio(Long id) {
        Reserva reserva = buscar(id);
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new BusinessException("Solo se puede marcar \"no vino\" en una reserva confirmada");
        }
        reserva.setEstado(EstadoReserva.CANCELADA);
        reserva.setNoAsistio(true);
        return guardarYAvisar(reserva);
    }

    private void validar(ReservaRequestDTO dto, Long idEditando) {
        LocalDateTime momento = dto.getFecha().atTime(dto.getHora());
        if (momento.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException("No se pueden hacer reservas en una fecha u hora que ya pasó");
        }

        ConfigLocal cfg = configLocalService.obtener();
        int maximo = cfg.limitePersonasReserva();
        if (dto.getCantidadPersonas() > maximo) {
            throw new BusinessException("El máximo por reserva es de " + maximo + " personas. Para grupos más grandes, comunicate con el local.");
        }

        Optional<HorarioLocal.Turno> turno = HorarioLocal.turnoDe(cfg, momento);
        if (turno.isEmpty()) {
            throw new BusinessException("El local está cerrado a esa hora. Elegí un día y horario de atención.");
        }

        if (cfg.tieneLimitePorTurno()) {
            int ocupadas = personasReservadasEnTurno(cfg, turno.get(), idEditando);
            int libres = cfg.getMaxPersonasTurno() - ocupadas;
            if (dto.getCantidadPersonas() > libres) {
                throw new BusinessException(libres <= 0
                        ? "No quedan lugares para ese turno. Probá con otro horario."
                        : "Para ese turno quedan lugares para " + libres + " personas.");
            }
        }
    }

    private int personasReservadasEnTurno(ConfigLocal cfg, HorarioLocal.Turno turno, Long excluirId) {
        LocalDate desde = turno.inicio();
        return reservaRepository
                .findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(desde, desde.plusDays(1), EstadoReserva.CONFIRMADA)
                .stream()
                .filter(r -> !Objects.equals(r.getId(), excluirId))
                .filter(r -> HorarioLocal.turnoDe(cfg, r.getFecha().atTime(r.getHora()))
                        .map(turno::equals).orElse(false))
                .mapToInt(r -> r.getCantidadPersonas() != null ? r.getCantidadPersonas() : 0)
                .sum();
    }

    private Reserva buscar(Long id) {
        return reservaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reserva", id));
    }

    private ReservaResponseDTO guardarYAvisar(Reserva reserva) {
        ReservaResponseDTO resultado = toDTO(reservaRepository.save(reserva));
        realtimeNotifier.avisarReservas();
        return resultado;
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public ReservaResponseDTO toDTO(Reserva r) {
        return ReservaResponseDTO.builder()
                .id(r.getId())
                .nombreCliente(r.getNombreCliente())
                .fecha(r.getFecha())
                .hora(r.getHora())
                .cantidadPersonas(r.getCantidadPersonas())
                .telefono(r.getTelefono())
                .aclaraciones(r.getAclaraciones())
                .estado(r.getEstado())
                .noAsistio(Boolean.TRUE.equals(r.getNoAsistio()))
                .cliente(r.getCliente() != null ? clienteService.toDTO(r.getCliente()) : null)
                .cancelable(r.getEstado() == EstadoReserva.CONFIRMADA)
                .build();
    }
}
