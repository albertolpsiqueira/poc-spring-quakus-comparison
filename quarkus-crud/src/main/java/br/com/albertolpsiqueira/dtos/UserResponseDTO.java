package br.com.albertolpsiqueira.dtos;

import br.com.albertolpsiqueira.models.UserModel;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record UserResponseDTO(
        Long id,
        String name,
        String email
){
    public static UserResponseDTO fromEntity(UserModel user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail());
    }
}
