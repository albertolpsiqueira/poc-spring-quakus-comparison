package br.com.albertolpsiqueira.spingbootcrud.dtos;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record UserRequestDTO(
   @NotBlank(message = "The name cannot be blank.")
   @Size(max = 50, message = "O nome deve ter no máximo 50 caracteres")
   String name,
   @NotBlank(message = "The email cannot be blank")
   @Email(message = "Invalid email format")
   @Size(max = 100, message = "The email must be a maximum of 100 characters long.")
   String email
) {}
