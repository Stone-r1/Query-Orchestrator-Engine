package org.example.models.dto;

public record BidderProfile(
        Long userId,
        Long totalBidsPlaced,
        Long auctionsParticipated,
        Double totalSpend,
        Double averageBidPerAuction
) {}
