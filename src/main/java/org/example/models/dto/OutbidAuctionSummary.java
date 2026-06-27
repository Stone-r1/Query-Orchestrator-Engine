package org.example.models.dto;

public record OutbidAuctionSummary(
        Long auctionId,
        String itemName,
        Long totalBids,
        Long outbidUserCount,
        Double currentMaxBid,
        Double startingPrice
) {}
