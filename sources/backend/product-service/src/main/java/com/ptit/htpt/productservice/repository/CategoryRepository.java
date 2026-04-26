package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {
  Optional<CategoryEntity> findBySlug(String slug);
}
