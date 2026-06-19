package org.example.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "auction")
@NoArgsConstructor
@AllArgsConstructor
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auctionId;

    private String itemName;
    private String itemDescription;
    private Double startingPrice;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Double maxBid;
    private Long sellerId;
    private Long winnerId;
}
