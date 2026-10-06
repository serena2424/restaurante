package com.gestorgastronomico.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

@Configuration
public class JacksonConfig {

    /**
     * Fechas y horas estrictas: "2027-02-31" o "25:00" se rechazan con 400 en
     * vez de convertirse en otra fecha (Jackson, por defecto, pasaba el 31/02 al 28/02).
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer fechasEstrictas() {
        DateTimeFormatter fecha = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter hora = DateTimeFormatter.ofPattern("HH:mm[:ss]").withResolverStyle(ResolverStyle.STRICT);
        return builder -> builder
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(fecha))
                .deserializerByType(LocalTime.class, new LocalTimeDeserializer(hora));
    }
}
