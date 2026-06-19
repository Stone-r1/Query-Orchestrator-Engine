package org.example.models.dto;

public record AuctionStats(
        Long auctionId,
        String itemName,
        Long bidCount,
        Double currentMaxBid
) {}
