package com.barclub.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "config_local")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ConfigLocal {

    @Id
    private Long id; // siempre será 1, fila única

    private String nombre;

    @Column(length = 500)
    private String dir;

    private String tel;

    @Column(length = 500)
    private String slogan;

    private String dias;
    @JsonProperty("mDesde")
    private String mDesde;
    @JsonProperty("mHasta")
    private String mHasta;
    @JsonProperty("nDesde")
    private String nDesde;
    @JsonProperty("nHasta")
    private String nHasta;

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

    // "auto", "open", "close"  — controlado desde el panel admin
    @Builder.Default
    private String estadoManual = "auto";

    // Logo del local — puede ser URL externa o base64 de imagen
    @Column(columnDefinition = "MEDIUMTEXT")
    private String logoUrl;

    // Imagen de portada/banner del hero — puede ser URL externa o base64 de imagen
    @Column(columnDefinition = "MEDIUMTEXT")
    private String heroUrl;

    // Posición del banner: "center", "top", "bottom", etc.
    @Builder.Default
    private String heroPos = "center";

    // Apariencia: color de acento (#rrggbb) y modo (dark/light). Viven en el servidor
    // para que la página pública los muestre a cualquier cliente, no solo en el navegador del admin.
    private String temaAccent;

    @Builder.Default
    private String temaMode = "dark";

    // Color de los textos que van sobre la foto de portada/hero (#rrggbb).
    // Vacío = color por defecto de la página pública. Configurable desde
    // Apariencia (pestaña "Texto de portada" del selector de color).
    private String herotextcolor;

    // Momento del último cierre de caja (ISO datetime). Vacío = nunca se cerró.
    // Estado de la caja: NO viaja en el JSON de /api/config (ni de ida ni de
    // vuelta). Antes sí viajaba, así que (1) cualquiera veía el estado de la
    // caja en la API pública, y (2) al guardar la Configuración el panel
    // mandaba la copia que había cargado antes: si mientras tanto alguien
    // cerró la caja, guardar la config la volvía a abrir y la próxima caja
    // contaba otra vez las ventas ya cerradas. La caja se maneja solo con
    // /api/ventas/caja, /cerrar-caja y /abrir-caja.
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String cierreCaja;

    // Si la caja está actualmente abierta para cobrar. Empieza en true (el
    // primer día no debería hacer falta abrirla a mano). Se pone en false al
    // cerrar caja, y hay que abrirla de nuevo explícitamente antes de poder
    // registrar otra venta — evita cobrar con la caja "cerrada" de la
    // jornada anterior sin que nadie se dé cuenta.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Builder.Default
    private Boolean cajaAbierta = true;

    // Emails habilitados para acceso rápido en el login del panel admin
    // Guardados como JSON array: ["email1@x.com","email2@x.com"]
    // Ya no se usa (los emails de acceso rápido se guardan en cada
    // dispositivo): la API pública los mostraba a cualquiera. Se deja la
    // columna para no romper bases existentes, pero nunca sale en el JSON.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(length = 1000)
    @Builder.Default
    private String loginEmails = "[]";

    // Métodos de pago que acepta el local, separados por coma (ej:
    // "EFECTIVO,TARJETA,TRANSFERENCIA"). Si es null o vacío, se aceptan todos.
    @Column(length = 100)
    private String pagosAceptados;
}
