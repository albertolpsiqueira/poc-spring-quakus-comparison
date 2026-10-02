package br.com.albertolpsiqueira.spingbootcrud.exceptions;

import java.time.LocalDateTime;

public record ErrorResponseDTO(LocalDateTime timestamp,int status, String error, String message) {

    public ErrorResponseDTO(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message);
    }

}
