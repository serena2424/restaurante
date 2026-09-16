package com.barclub;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BarClubApplication {

    // Fuerza la zona horaria de Argentina en toda la aplicación.
    // Sin esto, en un servidor en la nube (Railway usa UTC) las horas de
    // pedidos, ventas y reservas salen corridas varias horas.
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BarClubApplication.class, args);
    }
}
