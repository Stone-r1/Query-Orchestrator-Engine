package org.example.models.dto;


import java.time.LocalDateTime;

public record BidRanks(
        Long auctionId,
        Double amount,
        LocalDateTime placedAt,
        Integer rank
) {}
