package com.gestorgastronomico;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GestorGastronomicoApplication {

    // Los servidores en la nube corren en UTC; las horas se guardan en hora argentina.
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    public static void main(String[] args) {
        SpringApplication.run(GestorGastronomicoApplication.class, args);
    }
}
