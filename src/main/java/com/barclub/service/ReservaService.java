package com.barclub.service;

import com.barclub.dto.ReservaRequestDTO;
import com.barclub.dto.ReservaResponseDTO;
import com.barclub.entity.Cliente;
import com.barclub.entity.Reserva;
import com.barclub.exception.BusinessException;
import com.barclub.exception.ResourceNotFoundException;
import com.barclub.repository.ClienteRepository;
import com.barclub.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
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
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final com.barclub.websocket.RealtimeNotifier realtimeNotifier;

    // Anti-duplicación: rechaza una reserva idéntica repetida en pocos segundos
    // (doble submit/reintento). No bloquea reservas legítimas distintas.
    private static final java.util.Map<String, Long> ULTIMAS_RESERVAS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long VENTANA_ANTIDUP_MS = 8000;
    private static synchronized boolean duplicadoReciente(String clave) {
        long ahora = System.currentTimeMillis();
        ULTIMAS_RESERVAS.values().removeIf(t -> ahora - t > VENTANA_ANTIDUP_MS);
        Long prev = ULTIMAS_RESERVAS.get(clave);
        if (prev != null && ahora - prev < VENTANA_ANTIDUP_MS) return true;
        ULTIMAS_RESERVAS.put(clave, ahora);
        return false;
    }

    // ---- Listar todas ----
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarTodas() {
        return reservaRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Reservas de hoy ----
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarDeHoy() {
        return reservaRepository.findByFechaAndEstado(LocalDate.now(), Reserva.EstadoReserva.CONFIRMADA)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Reservas próximas (hoy + los próximos N días) ----
    // Pensado para que el panel avise de reservas nuevas SIN IMPORTAR la
    // fecha (antes solo se enteraban de las reservas de hoy — una reserva
    // para dentro de unos días no generaba ningún aviso hasta esa fecha) y
    // para armar el recordatorio de "mañana tenés una reserva".
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarProximas(int dias) {
        LocalDate desde = LocalDate.now();
        LocalDate hasta = desde.plusDays(Math.max(dias, 0));
        return reservaRepository.findByFechaBetweenAndEstadoOrderByFechaAscHoraAsc(
                        desde, hasta, Reserva.EstadoReserva.CONFIRMADA)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Reservas por fecha ----
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarPorFecha(LocalDate fecha) {
        return reservaRepository.findByFecha(fecha)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ---- Obtener por id ----
    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerPorId(Long id) {
        return toDTO(reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id)));
    }

    // ---- Crear reserva ----
    public ReservaResponseDTO crear(ReservaRequestDTO dto) {
        // Validar que la fecha no sea pasada
        if (dto.getFecha().isBefore(LocalDate.now())) {
            throw new BusinessException("No se pueden hacer reservas en fechas pasadas");
        }
        // Si la reserva es para hoy, la hora no puede ser una que ya pasó.
        if (dto.getFecha().isEqual(LocalDate.now())
                && dto.getHora() != null
                && dto.getHora().isBefore(LocalTime.now())) {
            throw new BusinessException("No se pueden hacer reservas en un horario que ya pasó");
        }

        // Anti-duplicado: misma reserva (teléfono, fecha, hora, nombre) en segundos → doble envío.
        String firmaRes = "RES|" + (dto.getTelefono() == null ? "" : dto.getTelefono().trim())
                + "|" + dto.getFecha() + "|" + dto.getHora()
                + "|" + (dto.getNombreCliente() == null ? "" : dto.getNombreCliente().trim());
        if (duplicadoReciente(firmaRes)) {
            throw new BusinessException("Ya recibimos esta reserva hace unos segundos. Esperá un momento antes de reenviarla.");
        }

        Reserva reserva = Reserva.builder()
                .nombreCliente(dto.getNombreCliente())
                .fecha(dto.getFecha())
                .hora(dto.getHora())
                .cantidadPersonas(dto.getCantidadPersonas())
                .telefono(dto.getTelefono())
                .aclaraciones(dto.getAclaraciones())
                .estado(Reserva.EstadoReserva.CONFIRMADA)
                .build();

        // Asociar cliente registrado si viene
        if (dto.getClienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.getClienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente", dto.getClienteId()));
            reserva.setCliente(cliente);
        }

        ReservaResponseDTO resultado = toDTO(reservaRepository.save(reserva));
        realtimeNotifier.avisarReservas();
        return resultado;
    }

    // ---- Actualizar reserva ----
    public ReservaResponseDTO actualizar(Long id, ReservaRequestDTO dto) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id));

        if (reserva.getEstado() == Reserva.EstadoReserva.CANCELADA) {
            throw new BusinessException("No se puede modificar una reserva cancelada");
        }

        reserva.setNombreCliente(dto.getNombreCliente());
        reserva.setFecha(dto.getFecha());
        reserva.setHora(dto.getHora());
        reserva.setCantidadPersonas(dto.getCantidadPersonas());
        reserva.setTelefono(dto.getTelefono());
        reserva.setAclaraciones(dto.getAclaraciones());

        ReservaResponseDTO resultado = toDTO(reservaRepository.save(reserva));
        realtimeNotifier.avisarReservas();
        return resultado;
    }

    // ---- Cancelar reserva ----
    public ReservaResponseDTO cancelar(Long id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id));

        if (reserva.getEstado() == Reserva.EstadoReserva.CANCELADA) {
            throw new BusinessException("La reserva ya está cancelada");
        }
        if (reserva.getEstado() == Reserva.EstadoReserva.COMPLETADA) {
            throw new BusinessException("No se puede cancelar una reserva ya completada");
        }

        reserva.setEstado(Reserva.EstadoReserva.CANCELADA);
        ReservaResponseDTO resultado = toDTO(reservaRepository.save(reserva));
        realtimeNotifier.avisarReservas();
        return resultado;
    }

    // ---- Marcar como completada ----
    public ReservaResponseDTO completar(Long id) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", id));

        if (reserva.getEstado() != Reserva.EstadoReserva.CONFIRMADA) {
            throw new BusinessException("Solo se pueden completar reservas confirmadas");
        }
        reserva.setEstado(Reserva.EstadoReserva.COMPLETADA);
        return toDTO(reservaRepository.save(reserva));
    }

    // ---- Verificar si es cancelable ----
    private boolean esCancelable(Reserva reserva) {
        return reserva.getEstado() == Reserva.EstadoReserva.CONFIRMADA;
    }

    // ---- Mapper ----
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
                .cliente(r.getCliente() != null ? clienteService.toDTO(r.getCliente()) : null)
                .cancelable(esCancelable(r))
                .build();
    }
}
