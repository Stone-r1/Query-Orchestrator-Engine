package org.example.models.dto;

public record AuctionBidderOverlap(
        Long auctionIdA,
        Long auctionIdB,
        Long sharedBidderCount
) {}
