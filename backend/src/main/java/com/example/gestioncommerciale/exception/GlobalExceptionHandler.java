package com.example.gestioncommerciale.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> erreurs = new HashMap<>();
        for (FieldError erreur : ex.getBindingResult().getFieldErrors()) {
            erreurs.put(erreur.getField(), erreur.getDefaultMessage());
        }
        Map<String, Object> body = corps(HttpStatus.BAD_REQUEST, "Erreur de validation");
        body.put("champs", erreurs);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(corps(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /**
     * Contrainte d'entite violee a l'enregistrement (@NotBlank sur une entite...).
     * Selon le moment ou Hibernate declenche la validation, l'exception arrive
     * soit brute, soit emballee dans une TransactionSystemException.
     * Sans ces handlers, l'erreur remonte en 500 et le champ fautif est perdu.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleContrainteEntite(ConstraintViolationException ex) {
        return reponseValidation(ex);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Map<String, Object>> handleValidationTransaction(TransactionSystemException ex) {
        if (ex.getMostSpecificCause() instanceof ConstraintViolationException cve) {
            return reponseValidation(cve);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(corps(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de l'enregistrement"));
    }

    private ResponseEntity<Map<String, Object>> reponseValidation(ConstraintViolationException ex) {
        Map<String, String> champs = new HashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            champs.put(v.getPropertyPath().toString(), v.getMessage());
        }
        Map<String, Object> body = corps(HttpStatus.BAD_REQUEST, "Erreur de validation");
        body.put("champs", champs);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Violation de contrainte en base (cle etrangere, unicite...).
     * Sans ce handler, ces cas remontent en 500 avec une stacktrace SQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrite(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(corps(HttpStatus.CONFLICT,
                        "Operation impossible : cet element est utilise ailleurs "
                                + "ou viole une contrainte d'unicite"));
    }

    /** Fichier uploade trop volumineux (limite spring.servlet.multipart). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTropGros(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(corps(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Fichier trop volumineux : la taille maximale autorisee est depassee"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(corps(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(corps(status, ex.getReason()));
    }

    private Map<String, Object> corps(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
