package br.com.albertolpsiqueira.spingbootcrud.dtos;

public record UserResponseDTO (
        Long id,
        String name,
        String email
){
    public static UserResponseDTO fromEntity(br.com.albertolpsiqueira.spingbootcrud.models.UserModel user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail());
    }
}
