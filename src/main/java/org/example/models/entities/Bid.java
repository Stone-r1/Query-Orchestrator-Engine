package org.example.models.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "bid")
@NoArgsConstructor
@AllArgsConstructor
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bid_id")
    private Long bidId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "auction_id")
    private Long auctionId;

    private Double amount;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;
}
