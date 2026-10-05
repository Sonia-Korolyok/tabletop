package com.tabletop.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    @Query("select t from Tag t where t.owner.id = :ownerId order by lower(t.name)")
    List<Tag> findAllByOwner(Long ownerId);

    @Query("select t from Tag t where t.owner.id = :ownerId and lower(t.name) in :lowerNames")
    List<Tag> findByOwnerAndLowerNames(Long ownerId, Collection<String> lowerNames);
}
