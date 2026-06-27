package org.example.models.dto;

public record CompetitiveBidSummary(
        Long bidId,
        Long userId,
        Double amount,
        Integer rankInAuction,
        Double gapToLeader,
        Double previousBidAmount
) {}
