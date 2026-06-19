package org.example.models.dto;


public record TopBidder(
        Long userId,
        Double highestBid
) {}
