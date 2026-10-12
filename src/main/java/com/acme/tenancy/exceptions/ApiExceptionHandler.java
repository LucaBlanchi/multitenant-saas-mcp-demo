package com.acme.tenancy.exceptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e, HttpServletRequest r) { return ResponseEntity.status(e.getStatus()).body(Map.of("timestamp", Instant.now(), "status", e.getStatus().value(), "code", e.getCode(), "message", e.getMessage(), "path", r.getRequestURI())); }
 @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class}) ResponseEntity<?> bad(Exception e, HttpServletRequest r) { String msg = e instanceof MethodArgumentNotValidException m ? m.getBindingResult().getFieldErrors().stream().map(x -> x.getField()+": "+x.getDefaultMessage()).findFirst().orElse("Invalid request") : e.getMessage(); return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400, "code", "BAD_REQUEST", "message", msg == null ? "Invalid request" : msg, "path", r.getRequestURI())); }
 @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e, HttpServletRequest r) { return ResponseEntity.status(500).body(Map.of("timestamp", Instant.now(), "status", 500, "code", "INTERNAL", "message", "Unexpected server error", "path", r.getRequestURI())); }
}
