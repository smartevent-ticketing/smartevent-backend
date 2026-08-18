package com.smartevent.modules.storage.repository;

import com.smartevent.modules.storage.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, UUID> {

    List<FileEntity> findByOwnerId(UUID ownerId);

    Optional<FileEntity> findByBucketNameAndObjectName(String bucketName, String objectName);
}

