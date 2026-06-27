package org.example.models.dto;

public record AuctionPriceVelocity(
        Long auctionId,
        Integer quartile,
        Long bidCount,
        Double maxBidInQuartile,
        Double priceGainFromPreviousQuartile
) {}
