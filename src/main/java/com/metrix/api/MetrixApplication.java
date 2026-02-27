package com.metrix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * METRIX - Sistema de Gestión de Ejecución Operativa y Analítica
 *
 * Clase principal de arranque de la aplicación.
 * Ruta: backend-api/src/main/java/com/metrix/MetrixApplication.java
 */
@SpringBootApplication
@EnableMongoAuditing  // Habilita @CreatedDate y @LastModifiedDate en las entidades
public class MetrixApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetrixApplication.class, args);
        System.out.println("-> METRIX API - Gestión Operativa y Analítica");
    }
}