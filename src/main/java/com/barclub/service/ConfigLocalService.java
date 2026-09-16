package com.barclub.service;

import com.barclub.entity.ConfigLocal;
import com.barclub.repository.ConfigLocalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigLocalService {

    private final ConfigLocalRepository repo;

    private static final Long ID = 1L;

    // Valores por defecto si nunca se guardó nada
    private ConfigLocal defaults() {
        return ConfigLocal.builder()
                .id(ID)
                .nombre("Mi Negocio")
                .dir("")
                .tel("")
                .slogan("Pedí online, elegí retiro o delivery, o reserva tu mesa.")
                .dias("")
                .mDesde("")
                .mHasta("")
                .nDesde("")
                .nHasta("")
                .horarioLibre("")
                .ig("").wa("").fb("")
                .radioDelivery(5)
                .costoDelivery(0)
                .minimo(0)
                .aceptaDelivery(true)
                .aceptaRetiro(true)
                .avisoOn(false)
                .avisoTxt("")
                .estadoManual("auto")
                .heroUrl("")
                .heroPos("center")
                .herotextcolor("")
                .build();
    }

    public ConfigLocal obtener() {
        return repo.findById(ID).orElseGet(() -> repo.save(defaults()));
    }

    public ConfigLocal guardar(ConfigLocal cfg) {
        cfg.setId(ID); // siempre fila única
        return repo.save(cfg);
    }
}
