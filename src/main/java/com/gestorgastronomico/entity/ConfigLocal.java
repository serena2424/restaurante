package com.gestorgastronomico.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

/**
 * Configuración del local. Es una sola fila (id = 1). Lo que está marcado con
 * {@link JsonIgnore} es estado interno y no viaja en /api/config, que es pública.
 */
@Entity
@Table(name = "config_local")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ConfigLocal {

    public static final int MAX_PERSONAS_RESERVA_POR_DEFECTO = 20;

    @Id
    private Long id;

    private String nombre;

    @Column(length = 500)
    private String dir;

    private String tel;

    @Column(length = 500)
    private String slogan;

    /** Días de atención separados por coma: 0 = domingo … 6 = sábado. */
    private String dias;
    @JsonProperty("mDesde")
    private String mDesde;
    @JsonProperty("mHasta")
    private String mHasta;
    @JsonProperty("nDesde")
    private String nDesde;
    @JsonProperty("nHasta")
    private String nHasta;

    /** Texto libre de horarios de versiones anteriores. Ya no se muestra. */
    @Column(length = 500)
    private String horarioLibre;

    private String ig;
    private String wa;
    private String fb;

    @Builder.Default
    private Integer radioDelivery = 5;

    @Builder.Default
    private Integer costoDelivery = 0;

    @Builder.Default
    private Integer minimo = 0;

    @Builder.Default
    private Boolean aceptaDelivery = true;

    @Builder.Default
    private Boolean aceptaRetiro = true;

    @Builder.Default
    private Boolean avisoOn = false;

    @Column(length = 500)
    private String avisoTxt;

    /** "auto" (según horarios), "open" o "close". */
    @Builder.Default
    private String estadoManual = "auto";

    /** URL o imagen en base64. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String logoUrl;

    /** Portada: URL o imagen en base64. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String heroUrl;

    @Builder.Default
    private String heroPos = "center";

    private String temaAccent;

    @Builder.Default
    private String temaMode = "dark";

    /** Color del texto sobre la portada (#rrggbb). Vacío = el de la página. */
    private String herotextcolor;

    /** Métodos de pago aceptados, separados por coma. Vacío = todos. */
    @Column(length = 100)
    private String pagosAceptados;

    /** Máximo de personas en una sola reserva. */
    private Integer maxPersonasReserva;

    /** Máximo de personas reservadas por turno. Vacío o 0 = sin límite. */
    private Integer maxPersonasTurno;

    /** Inicio de la caja actual (cuándo se abrió) en formato ISO. */
    @JsonIgnore
    private String cierreCaja;

    @JsonIgnore
    @Builder.Default
    private Boolean cajaAbierta = true;

    /** Columna de versiones anteriores; los accesos rápidos ahora se guardan en cada dispositivo. */
    @JsonIgnore
    @Column(length = 1000)
    @Builder.Default
    private String loginEmails = "[]";

    public int limitePersonasReserva() {
        return maxPersonasReserva != null && maxPersonasReserva > 0
                ? maxPersonasReserva : MAX_PERSONAS_RESERVA_POR_DEFECTO;
    }

    public boolean tieneLimitePorTurno() {
        return maxPersonasTurno != null && maxPersonasTurno > 0;
    }
}
