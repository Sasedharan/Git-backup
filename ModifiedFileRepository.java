package com.gitrepository.gitrepository.repository;

import com.gitrepository.gitrepository.entity.ModifiedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModifiedFileRepository extends JpaRepository<ModifiedFileEntity, Long> {
}
