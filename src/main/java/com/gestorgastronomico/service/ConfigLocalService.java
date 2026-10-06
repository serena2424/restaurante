package com.gestorgastronomico.service;

import com.gestorgastronomico.entity.ConfigLocal;
import com.gestorgastronomico.entity.MetodoPago;
import com.gestorgastronomico.exception.BusinessException;
import com.gestorgastronomico.repository.ConfigLocalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigLocalService {

    public static final int MAX_LARGO_NOMBRE = 60;
    private static final Long ID = 1L;
    private static final int MAX_PERSONAS_RESERVA = 200;

    private final ConfigLocalRepository repo;

    public ConfigLocal obtener() {
        return repo.findById(ID).orElseGet(() -> repo.save(porDefecto()));
    }

    /**
     * Guarda la configuración que manda el panel. El estado de la caja no viaja
     * en el JSON, así que se conserva el que está en la base.
     */
    public ConfigLocal guardarDesdePanel(ConfigLocal cfg) {
        validar(cfg);
        ConfigLocal actual = obtener();
        cfg.setCierreCaja(actual.getCierreCaja());
        cfg.setCajaAbierta(actual.getCajaAbierta());
        cfg.setLoginEmails("[]");
        cfg.setHorarioLibre("");
        cfg.setNombre(cfg.getNombre().trim());
        return guardar(cfg);
    }

    /** Abre, cierra o vuelve a modo automático sin tocar el resto de la configuración. */
    public ConfigLocal cambiarEstadoManual(String modo) {
        if (!Set.of("auto", "open", "close").contains(modo)) {
            throw new BusinessException("Estado inválido: usá auto, open o close.");
        }
        ConfigLocal cfg = obtener();
        cfg.setEstadoManual(modo);
        return guardar(cfg);
    }

    public ConfigLocal guardar(ConfigLocal cfg) {
        cfg.setId(ID);
        return repo.save(cfg);
    }

    /** Copia de la configuración sin logo ni portada, para los refrescos periódicos de la web. */
    public ConfigLocal obtenerSinImagenes() {
        return obtener().toBuilder().logoUrl(null).heroUrl(null).build();
    }

    void validar(ConfigLocal cfg) {
        String nombre = cfg.getNombre() == null ? "" : cfg.getNombre().trim();
        if (nombre.isEmpty()) {
            throw new BusinessException("El nombre del local es obligatorio.");
        }
        if (nombre.length() > MAX_LARGO_NOMBRE) {
            throw new BusinessException("El nombre del local puede tener hasta " + MAX_LARGO_NOMBRE + " caracteres.");
        }
        if (negativo(cfg.getCostoDelivery())) {
            throw new BusinessException("El costo de envío no puede ser negativo.");
        }
        if (negativo(cfg.getMinimo())) {
            throw new BusinessException("El pedido mínimo no puede ser negativo.");
        }
        if (negativo(cfg.getRadioDelivery())) {
            throw new BusinessException("El radio de delivery no puede ser negativo.");
        }
        String pagos = cfg.getPagosAceptados();
        if (pagos != null && !pagos.isBlank() && metodosValidos(pagos).isEmpty()) {
            throw new BusinessException("Elegí al menos un medio de pago.");
        }
        Integer porReserva = cfg.getMaxPersonasReserva();
        if (porReserva != null && (porReserva < 1 || porReserva > MAX_PERSONAS_RESERVA)) {
            throw new BusinessException("El máximo de personas por reserva tiene que estar entre 1 y " + MAX_PERSONAS_RESERVA + ".");
        }
        if (negativo(cfg.getMaxPersonasTurno())) {
            throw new BusinessException("El máximo de personas por turno no puede ser negativo.");
        }
        String errorTurnos = HorarioLocal.validarTurnos(cfg);
        if (errorTurnos != null) {
            throw new BusinessException(errorTurnos);
        }
    }

    private static boolean negativo(Integer valor) {
        return valor != null && valor < 0;
    }

    private static Set<String> metodosValidos(String texto) {
        Set<String> nombres = Arrays.stream(MetodoPago.values()).map(Enum::name).collect(Collectors.toSet());
        return Arrays.stream(texto.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(nombres::contains)
                .collect(Collectors.toSet());
    }

    private ConfigLocal porDefecto() {
        return ConfigLocal.builder()
                .id(ID)
                .nombre("Mi Negocio")
                .dir("")
                .tel("")
                .slogan("Pedí online, elegí retiro o delivery, o reservá tu mesa.")
                .dias("")
                .mDesde("")
                .mHasta("")
                .nDesde("")
                .nHasta("")
                .horarioLibre("")
                .ig("").wa("").fb("")
                .avisoTxt("")
                .heroUrl("")
                .herotextcolor("")
                .maxPersonasReserva(ConfigLocal.MAX_PERSONAS_RESERVA_POR_DEFECTO)
                .build();
    }
}
