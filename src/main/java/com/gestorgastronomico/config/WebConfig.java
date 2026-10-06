package com.gestorgastronomico.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class WebConfig {

    /**
     * Agrega ETag a las respuestas públicas que más se repiten. Si nada cambió,
     * el navegador recibe un 304 sin cuerpo en vez de volver a bajar la
     * configuración (con logo y portada) o la carta completa.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registro =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registro.addUrlPatterns("/api/config", "/api/config/estado", "/api/productos/activos");
        registro.setName("etagFilter");
        return registro;
    }
}
