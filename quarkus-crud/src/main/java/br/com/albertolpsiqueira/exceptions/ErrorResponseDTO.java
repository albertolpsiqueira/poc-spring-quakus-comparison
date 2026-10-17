package br.com.albertolpsiqueira.exceptions;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
public record ErrorResponseDTO(LocalDateTime timestamp, int status, String error, String message) {

    public ErrorResponseDTO(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message);
    }

}
