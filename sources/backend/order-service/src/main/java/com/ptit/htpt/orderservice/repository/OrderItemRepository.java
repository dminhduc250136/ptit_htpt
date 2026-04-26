package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, String> {}
