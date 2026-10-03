package br.com.albertolpsiqueira.resources;

import br.com.albertolpsiqueira.dtos.UserRequestDTO;
import br.com.albertolpsiqueira.dtos.UserResponseDTO;
import br.com.albertolpsiqueira.services.UserService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private final UserService userService;

    public UserResource(UserService userService) {
        this.userService = userService;
    }

    @POST
    public Response create(@Valid UserRequestDTO requestDTO) {
        UserResponseDTO created = userService.create(requestDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    public List<UserResponseDTO> findAll() {
        return userService.findAll();
    }

    @GET
    @Path("/{id}")
    public UserResponseDTO findById(@PathParam("id") Long id) {
        return userService.findById(id);
    }

    @PUT
    @Path("/{id}")
    public UserResponseDTO update(@PathParam("id") Long id, @Valid UserRequestDTO requestDTO) {
        return userService.update(id, requestDTO);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        userService.delete(id);
        return Response.noContent().build();
    }

}
