package com.maxx_global.repository;

import com.maxx_global.entity.CartItem;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByIdAndCartIdAndCartUserIdAndStatus(Long id, Long cartId, Long userId, EntityStatus status);

    Optional<CartItem> findByCartIdAndProductPriceIdAndStatus(Long cartId, Long productPriceId, EntityStatus status);
}
