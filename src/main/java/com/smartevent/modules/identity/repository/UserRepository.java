package com.smartevent.modules.identity.repository;

import com.smartevent.modules.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND "
            + "(LOWER(u.email) LIKE :pattern OR LOWER(u.fullName) LIKE :pattern)")
    Page<User> searchAdminUsers(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.id IN :ids")
    List<User> findAllByIdsWithRoles(@Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findNotDeletedByIdForRoleUpdate(@Param("id") UUID id);
}

