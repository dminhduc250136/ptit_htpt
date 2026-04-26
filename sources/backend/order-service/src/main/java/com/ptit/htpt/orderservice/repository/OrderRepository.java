package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
  List<OrderEntity> findByUserId(String userId);
}
