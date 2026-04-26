package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ProductEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
  Optional<ProductEntity> findBySlug(String slug);
}
