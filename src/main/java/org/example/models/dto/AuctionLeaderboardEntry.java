package org.example.models.dto;

public record AuctionLeaderboardEntry(
        Long auctionId,
        Long userId,
        Double topBid,
        Integer rank,
        Long bidCount,
        Double spreadFromStartingPrice
) {}
