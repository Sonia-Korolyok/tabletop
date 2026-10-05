package com.tabletop.folder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    @Query("select f from Folder f where f.owner.id = :ownerId order by lower(f.name)")
    List<Folder> findAllByOwner(Long ownerId);

    Optional<Folder> findByIdAndOwnerId(Long id, Long ownerId);

    List<Folder> findAllByIdInAndOwnerId(Collection<Long> ids, Long ownerId);
}
