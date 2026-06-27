package org.example.models.dto;

import java.time.LocalDateTime;

public record BidIntensitySlot(
        LocalDateTime minute,
        Long bidsInSlot,
        Double maxBidInSlot,
        Long cumulativeBidCount
) {}
