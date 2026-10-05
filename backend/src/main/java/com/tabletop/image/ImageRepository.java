package com.tabletop.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByIdAndOwnerId(Long id, Long ownerId);
}
