package com.hackathon.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private Integer retryCount;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime failedAt;
}
