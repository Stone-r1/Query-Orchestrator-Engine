package org.example.models.dto;

public record UserSpendRanking(
        Long userId,
        Double totalSpend,
        Long auctionsParticipated,
        Integer spendRank
) {}
