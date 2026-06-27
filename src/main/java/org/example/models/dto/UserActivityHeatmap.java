package org.example.models.dto;

public record UserActivityHeatmap(
        Long userId,
        Integer hourOfDay,
        Long bidCount,
        Double totalSpend
) {}
