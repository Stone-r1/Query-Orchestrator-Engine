package org.example.steps.jdbc;

import org.example.models.dto.*;
import org.example.queries.jdbc.AuctionQueries;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class AuctionSteps {

    private final AuctionQueries auctionQueries;

    public AuctionSteps(AuctionQueries auctionQueries) {
        this.auctionQueries = auctionQueries;
    }

    public long insertAuction(
            String itemName,
            String itemDescription,
            Double startingPrice,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Double maxBid,
            Long sellerId,
            Long winnerId
    ) throws SQLException {
        return auctionQueries.insertAuction(
                itemName, itemDescription, startingPrice,
                startDate, endDate, maxBid, sellerId, winnerId
        );
    }

    public void clearAuctions() throws SQLException {
        auctionQueries.deleteAllAuctions();
    }

    public void clearAuction(
            long auctionId
    ) throws SQLException {
        auctionQueries.deleteAuctionById(auctionId);
    }

    public void validateAuctionStats(
            Long auctionId,
            long expectedBidCount,
            Double expectedMaxBid
    ) throws SQLException {
        AuctionStats stats = auctionQueries.getAuctionStats(auctionId);

        assertThat(stats.bidCount())
                .as("auction %d bid count", auctionId)
                .isEqualTo(expectedBidCount);
        assertThat(stats.currentMaxBid())
                .as("auction %d current max bid", auctionId)
                .isEqualTo(expectedMaxBid);
    }

    public void validateAuctionAppearsForMinBid(
            Double minBid,
            Long expectedAuctionId
    ) throws SQLException {
        List<Long> ids = auctionQueries.findAuctionIdsWithMinMaxBid(minBid);

        assertThat(ids)
                .as("auctions with maxBid >= %s should contain auction %d", minBid, expectedAuctionId)
                .contains(expectedAuctionId);
    }

    public void validateAuctionLeaderboard(
            Long auctionId,
            int expectedBidderCount,
            Long expectedRank1UserId
    ) throws SQLException {
        List<AuctionLeaderboardEntry> leaderboard = auctionQueries.findAuctionLeaderboard(auctionId);

        assertThat(leaderboard)
                .as("auction %d leaderboard size", auctionId)
                .hasSize(expectedBidderCount);

        assertThat(leaderboard.get(0).userId())
                .as("auction %d rank-1 user", auctionId)
                .isEqualTo(expectedRank1UserId);

        assertThat(leaderboard)
                .as("all spreads must be non-negative")
                .allSatisfy(entry ->
                        assertThat(entry.spreadFromStartingPrice())
                                .as("spread for user %d in auction %d", entry.userId(), auctionId)
                                .isGreaterThanOrEqualTo(0.0)
                );
    }

    public void validateAuctionHasOutbidUsers(
            Long expectedAuctionId,
            long minBidCount,
            long expectedOutbidUserCount
    ) throws SQLException {
        List<OutbidAuctionSummary> summaries = auctionQueries.findAuctionsWithOutbidUsers(minBidCount);

        OutbidAuctionSummary match = summaries.stream()
                .filter(s -> s.auctionId().equals(expectedAuctionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Auction %d not found in outbid summary with minBidCount=%d"
                                .formatted(expectedAuctionId, minBidCount)
                ));

        assertThat(match.outbidUserCount())
                .as("auction %d outbid user count", expectedAuctionId)
                .isEqualTo(expectedOutbidUserCount);
    }

    public void validateAuctionPriceVelocity(
            Long auctionId
    ) throws SQLException {
        List<AuctionPriceVelocity> quartiles = auctionQueries.findAuctionPriceVelocity(auctionId);

        assertThat(quartiles)
                .as("auction %d must produce exactly 4 price velocity quartiles", auctionId)
                .hasSize(4);

        assertThat(quartiles.get(0).priceGainFromPreviousQuartile())
                .as("auction %d Q1 price gain must be 0 (baseline quartile)", auctionId)
                .isEqualTo(0.0);

        for (int i = 1; i < quartiles.size(); i++) {
            assertThat(quartiles.get(i).maxBidInQuartile())
                    .as("auction %d quartile %d max bid must be >= quartile %d",
                            auctionId, i + 1, i)
                    .isGreaterThanOrEqualTo(quartiles.get(i - 1).maxBidInQuartile());
        }
    }

    public void validateCrossAuctionBidderOverlap(
            Long auctionIdA,
            Long auctionIdB,
            long expectedSharedBidders
    ) throws SQLException {
        List<AuctionBidderOverlap> overlaps = auctionQueries.findCrossAuctionBidderOverlap();

        AuctionBidderOverlap match = overlaps.stream()
                .filter(o -> o.auctionIdA().equals(auctionIdA) && o.auctionIdB().equals(auctionIdB))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No overlap found between auction %d and auction %d".formatted(auctionIdA, auctionIdB)
                ));

        assertThat(match.sharedBidderCount())
                .as("shared bidder count between auction %d and %d", auctionIdA, auctionIdB)
                .isEqualTo(expectedSharedBidders);

        assertThat(overlaps)
                .as("all overlap pairs must have at least one shared bidder")
                .allSatisfy(o ->
                        assertThat(o.sharedBidderCount())
                                .as("overlap between %d and %d", o.auctionIdA(), o.auctionIdB())
                                .isGreaterThan(0L)
                );
    }
}
