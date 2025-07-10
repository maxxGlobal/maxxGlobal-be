package com.maxx_global.repository;

import com.maxx_global.entity.Dealer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DealerRepository extends JpaRepository<Dealer, Long> {
    List<Dealer> findByNameContainingIgnoreCase(String name);
}
