package br.com.albertolpsiqueira.services;

import br.com.albertolpsiqueira.dtos.UserRequestDTO;
import br.com.albertolpsiqueira.dtos.UserResponseDTO;
import br.com.albertolpsiqueira.exceptions.EmailAlreadyExistsException;
import br.com.albertolpsiqueira.exceptions.ResourceNotFoundException;
import br.com.albertolpsiqueira.models.UserModel;
import br.com.albertolpsiqueira.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponseDTO create(UserRequestDTO requestDTO) {
        if (userRepository.existsByEmail(requestDTO.email())) {
            throw new EmailAlreadyExistsException("The e-mail '"+requestDTO.email()+"' is already registered");
        }
        UserModel user = new UserModel();
        user.setName(requestDTO.name());
        user.setEmail(requestDTO.email());
        UserModel savedUser = userRepository.save(user);
        return UserResponseDTO.fromEntity(savedUser);
    }

    @Transactional
    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream()
                .map(UserResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponseDTO findById(Long id) {
        UserModel user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + id + " não encontrado."));
        return UserResponseDTO.fromEntity(user);
    }

    @Transactional
    public UserResponseDTO update(Long id, UserRequestDTO requestDTO) {
        UserModel user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + id + " não encontrado."));

        // Se o e-mail foi alterado, verifica se o novo e-mail já pertence a outro usuário
        if (!user.getEmail().equals(requestDTO.email()) && userRepository.existsByEmail(requestDTO.email())) {
            throw new EmailAlreadyExistsException("O e-mail '" + requestDTO.email() + "' já está cadastrado.");
        }

        user.setName(requestDTO.name());
        user.setEmail(requestDTO.email());

        UserModel updatedUser = userRepository.save(user);
        return UserResponseDTO.fromEntity(updatedUser);
    }

    @Transactional
    public void delete(Long id) {
        UserModel user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + id + " não encontrado."));
        userRepository.delete(user);
    }

}
