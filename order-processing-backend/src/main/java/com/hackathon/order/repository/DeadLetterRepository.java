package com.hackathon.order.repository;

import com.hackathon.order.entity.DeadLetterOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterOrder, Long> {
    Optional<DeadLetterOrder> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);
}
