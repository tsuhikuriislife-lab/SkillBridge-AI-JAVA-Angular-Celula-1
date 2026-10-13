package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
    // Búsqueda parcial por nombre: "lu" encuentra "Luis", "Lucía"
    List<UserEntity> findByNameContainingIgnoreCase(String name);

    // Búsqueda parcial por email
    List<UserEntity> findByEmailContainingIgnoreCase(String email);

    // Orden alfabético
    List<UserEntity> findAllByOrderByNameAsc();

    List<UserEntity> findByRole(Role role);

    long countByRole(Role role);

    // Regla: usuarios de un servicio = SOLO los de ese servicio (join con user_services)
    @Query(value = """
            SELECT u.* FROM app_users u
            JOIN user_services us ON us.user_id = u.id
            WHERE us.service_id = :serviceId
            """, nativeQuery = true)
    List<UserEntity> findByServiceId(@Param("serviceId") UUID serviceId);
}
