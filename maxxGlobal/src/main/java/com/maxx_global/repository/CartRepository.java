package com.maxx_global.repository;

import com.maxx_global.entity.Cart;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.productVariant", "items.productPrice"})
    Optional<Cart> findByIdAndUserIdAndStatus(Long id, Long userId, EntityStatus status);

    @EntityGraph(attributePaths = {"items", "items.productVariant", "items.productPrice"})
    Optional<Cart> findByUserIdAndDealerIdAndStatus(Long userId, Long dealerId, EntityStatus status);
}
