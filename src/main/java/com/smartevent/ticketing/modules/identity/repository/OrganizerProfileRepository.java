package com.smartevent.ticketing.modules.identity.repository;

import com.smartevent.ticketing.modules.identity.entity.OrganizerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, UUID> {

    Optional<OrganizerProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
