package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final JpaUserRepository repository;

    public UserPersistenceAdapter(JpaUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserAccount save(UserAccount userAccount) {
        return toDomain(repository.save(toEntity(userAccount)));
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    @Override
    public List<UserAccount> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<UserAccount> findAll(int page, int size) {
        var result = repository.findAll(PageRequest.of(page, size, Sort.by("name").ascending()));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalPages(), result.getTotalElements(), result.getNumber());
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public List<UserAccount> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserAccount> searchByEmail(String email) {
        return repository.findByEmailContainingIgnoreCase(email).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserAccount> findAllSortedByName() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserAccount> findByServiceId(UUID serviceId) {
        return repository.findByServiceId(serviceId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserAccount> findByRole(Role role) {
        return repository.findByRole(role).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByRole(Role role) {
        return repository.countByRole(role);
    }

    // Conversión entidad ↔ dominio
    private UserEntity toEntity(UserAccount u) {
        return new UserEntity(
            u.id(), u.name(), u.email(), u.role(), u.image(), u.password(),
            u.gender(), u.birthDate(), u.status(), u.createdAt(), u.updatedAt());
    }

    private UserAccount toDomain(UserEntity e) {
        return new UserAccount(
            e.getId(), e.getName(), e.getEmail(), e.getRole(), e.getImage(), e.getPassword(),
            e.getGender(), e.getBirthDate(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
