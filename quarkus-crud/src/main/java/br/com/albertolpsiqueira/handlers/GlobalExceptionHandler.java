package br.com.albertolpsiqueira.handlers;

import br.com.albertolpsiqueira.exceptions.EmailAlreadyExistsException;
import br.com.albertolpsiqueira.exceptions.ErrorResponseDTO;
import br.com.albertolpsiqueira.exceptions.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.HashMap;
import java.util.Map;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public Response handleNotFound(ResourceNotFoundException ex) {
        return json(Response.Status.NOT_FOUND, new ErrorResponseDTO(404, "Not Found", ex.getMessage()));
    }

    @ServerExceptionMapper
    public Response handleEmailExists(EmailAlreadyExistsException ex) {
        return json(Response.Status.CONFLICT, new ErrorResponseDTO(409, "Conflict", ex.getMessage()));
    }

    @ServerExceptionMapper
    public Response handleValidation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            errors.put(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
        });
        return json(Response.Status.BAD_REQUEST, errors);
    }

    @ServerExceptionMapper
    public Response handleGeneric(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse(); // preserva 404 de rota, 405, 415 etc.
        }
        return json(Response.Status.INTERNAL_SERVER_ERROR,
                new ErrorResponseDTO(500, "Internal Server Error", "Ocorreu um erro inesperado no servidor."));
    }

    private Response json(Response.Status status, Object body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

}
