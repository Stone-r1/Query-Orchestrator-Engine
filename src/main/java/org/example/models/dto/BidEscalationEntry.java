package org.example.models.dto;

public record BidEscalationEntry(
        Long bidId,
        Long userId,
        Double amount,
        Double previousLeaderAmount,
        Double escalationAmount
) {}
