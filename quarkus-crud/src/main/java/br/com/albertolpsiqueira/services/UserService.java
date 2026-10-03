package br.com.albertolpsiqueira.services;

import br.com.albertolpsiqueira.dtos.UserRequestDTO;
import br.com.albertolpsiqueira.dtos.UserResponseDTO;
import br.com.albertolpsiqueira.exceptions.EmailAlreadyExistsException;
import br.com.albertolpsiqueira.exceptions.ResourceNotFoundException;
import br.com.albertolpsiqueira.models.UserModel;
import br.com.albertolpsiqueira.repositories.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponseDTO create(UserRequestDTO requestDTO) {
        if (userRepository.existsByEmail(requestDTO.email())) {
            throw new EmailAlreadyExistsException("The e-mail '"+requestDTO.email()+"' is already registred");
        }
        UserModel user = new UserModel();
        user.setName(requestDTO.name());
        user.setEmail(requestDTO.email());
        userRepository.persist(user);
        return UserResponseDTO.fromEntity(user);
    }

    public List<UserResponseDTO> findAll() {
        return userRepository.listAll().stream().map(UserResponseDTO::fromEntity).toList();
    }

    public UserResponseDTO findById(Long id) {
        UserModel user = userRepository.findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + id + " não encontrado."));
        return UserResponseDTO.fromEntity(user);
    }

    @Transactional
    public UserResponseDTO update(Long id, UserRequestDTO requestDTO) {
        UserModel user = userRepository.findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + id + " não encontrado."));

        if (!user.getEmail().equals(requestDTO.email()) && userRepository.existsByEmail(requestDTO.email())) {
            throw new EmailAlreadyExistsException("O e-mail '" + requestDTO.email() + "' já está cadastrado.");
        }

        user.setName(requestDTO.name());
        user.setEmail(requestDTO.email());
        return UserResponseDTO.fromEntity(user); // entidade gerenciada: o flush acontece no commit
    }

    @Transactional
    public void delete(Long id) {
        UserModel user = userRepository.findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + id + " não encontrado."));
        userRepository.delete(user);
    }

}
