package com.hackathon.order.service;

import com.hackathon.order.dto.DashboardStats;
import com.hackathon.order.entity.OrderStatus;
import com.hackathon.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;

    public DashboardStats getStats() {
        long total = orderRepository.count();
        long completed = orderRepository.countByStatus(OrderStatus.COMPLETED);
        long failed = orderRepository.countByStatus(OrderStatus.OUT_OF_STOCK);
        long processing = orderRepository.countByStatus(OrderStatus.PROCESSING);
        long retrying = orderRepository.countByStatus(OrderStatus.RETRYING);
        long deadLetter = orderRepository.countByStatus(OrderStatus.DEAD_LETTER);
        long pending = orderRepository.countByStatus(OrderStatus.PENDING);
        long ordersPerMinute = orderRepository.countOrdersSince(LocalDateTime.now().minusMinutes(1));

        return new DashboardStats(total, completed, failed, processing, retrying, deadLetter, pending, ordersPerMinute);
    }
}
