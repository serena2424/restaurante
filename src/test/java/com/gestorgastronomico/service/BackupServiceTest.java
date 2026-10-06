package com.gestorgastronomico.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    @Test
    void urlConUsuarioYClave_comoLaDeRailway() {
        BackupService.DatosConexion datos = BackupService.interpretarUrl(
                "jdbc:mysql://root:AbC123@mysql.railway.internal:3306/railway", "otro", "otra");

        assertEquals("mysql.railway.internal", datos.host());
        assertEquals("3306", datos.puerto());
        assertEquals("railway", datos.base());
        assertEquals("root", datos.usuario());
        assertEquals("AbC123", datos.clave());
    }

    @Test
    void urlSinCredenciales_usaLasVariablesAparte() {
        BackupService.DatosConexion datos = BackupService.interpretarUrl(
                "jdbc:mysql://localhost/gestor?useSSL=false", "root", "secreta");

        assertEquals("localhost", datos.host());
        assertEquals("3306", datos.puerto());
        assertEquals("gestor", datos.base());
        assertEquals("root", datos.usuario());
        assertEquals("secreta", datos.clave());
    }

    @Test
    void urlInvalida_explicaQueVariableRevisar() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BackupService.interpretarUrl("postgres://x", "u", "p"));

        assertTrue(error.getMessage().contains("DB_URL"));
    }
}
