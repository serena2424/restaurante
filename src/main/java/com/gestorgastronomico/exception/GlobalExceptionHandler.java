package com.gestorgastronomico.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        logger.warn("No encontrado: {}", ex.getMessage());
        return respuesta(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleRutaInexistente(NoResourceFoundException ex) {
        return respuesta(HttpStatus.NOT_FOUND, "No encontrado", "Esa dirección no existe.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMetodo(HttpRequestMethodNotSupportedException ex) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", "Esa operación no existe.", null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        logger.warn("Regla de negocio: {}", ex.getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, "Error de negocio", ex.getMessage(), null);
    }

    /** El mensaje principal es el del primer campo inválido, para que el panel lo muestre tal cual. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        String mensaje = campos.isEmpty() ? "Hay datos inválidos." : campos.values().iterator().next();
        logger.warn("Validación: {}", campos);
        return respuesta(HttpStatus.BAD_REQUEST, "Error de validación", mensaje, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonInvalido(HttpMessageNotReadableException ex) {
        String mensaje = contieneCausa(ex, DateTimeException.class)
                ? "La fecha o la hora no es válida."
                : "Los datos enviados no tienen el formato esperado.";
        return respuesta(HttpStatus.BAD_REQUEST, "Formato inválido", mensaje, null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleParametro(Exception ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Parámetro inválido", "Falta un dato o tiene un valor inválido.", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Argumento inválido: {}", ex.getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, "Argumento inválido", ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        logger.error("Error no controlado ({})", ex.getClass().getSimpleName(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor",
                "Ocurrió un error inesperado. Probá de nuevo y, si sigue, avisale al administrador.", null);
    }

    private static boolean contieneCausa(Throwable ex, Class<? extends Throwable> tipo) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (tipo.isInstance(t)) return true;
        }
        return false;
    }

    private static ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String error, String mensaje,
                                                           Map<String, String> campos) {
        ErrorResponse cuerpo = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(mensaje)
                .campos(campos)
                .build();
        return ResponseEntity.status(status).body(cuerpo);
    }
}
