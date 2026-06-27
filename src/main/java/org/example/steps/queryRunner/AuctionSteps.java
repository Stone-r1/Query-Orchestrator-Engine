package org.example.steps.queryRunner;

import org.example.models.dto.AuctionBidderOverlap;
import org.example.models.dto.AuctionLeaderboardEntry;
import org.example.models.dto.AuctionPriceVelocity;
import org.example.models.dto.AuctionStats;
import org.example.models.dto.OutbidAuctionSummary;
import org.example.models.entities.Auction;
import org.example.queries.queryRunner.AuctionQueries;
import org.hibernate.Session;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class AuctionSteps {

    private final AuctionQueries auctionQueries = new AuctionQueries();

    public void insertAuction(
            Session session,
            Auction auction
    ) {
        auctionQueries.insertAuction(session, auction);
    }

    public void clearAuctions(
            Session session
    ) {
        auctionQueries.deleteAllAuctions(session);
    }

    public void clearAuction(
            Session session,
            long auctionId
    ) {
        auctionQueries.deleteAuctionById(session, auctionId);
    }

    public void validateAuctionStats(
            Session session,
            Long auctionId,
            long expectedBidCount,
            Double expectedMaxBid
    ) {
        AuctionStats stats = auctionQueries.getAuctionStats(session, auctionId);

        assertThat(stats.bidCount())
                .as("auction %d bid count", auctionId)
                .isEqualTo(expectedBidCount);
        assertThat(stats.currentMaxBid())
                .as("auction %d current max bid", auctionId)
                .isEqualTo(expectedMaxBid);
    }

    public void validateAuctionAppearsForMinBid(
            Session session,
            Double minBid,
            Long expectedAuctionId
    ) {
        List<Auction> auctions = auctionQueries.findByMinMaxBid(session, minBid);

        assertThat(auctions)
                .as("auctions with maxBid >= %s should contain auction %d", minBid, expectedAuctionId)
                .extracting(Auction::getAuctionId)
                .contains(expectedAuctionId);
    }

    public void validateAuctionLeaderboard(
            Session session,
            Long auctionId,
            int expectedBidderCount,
            Long expectedRank1UserId
    ) {
        List<AuctionLeaderboardEntry> leaderboard = auctionQueries.findAuctionLeaderboard(session, auctionId);

        assertThat(leaderboard)
                .as("auction %d leaderboard size", auctionId)
                .hasSize(expectedBidderCount);

        assertThat(leaderboard.get(0).userId())
                .as("auction %d rank-1 user", auctionId)
                .isEqualTo(expectedRank1UserId);

        assertThat(leaderboard)
                .as("all spreads must be non-negative — every top bid must exceed starting price")
                .allSatisfy(entry ->
                        assertThat(entry.spreadFromStartingPrice())
                                .as("spread for user %d in auction %d", entry.userId(), auctionId)
                                .isGreaterThanOrEqualTo(0.0)
                );
    }

    public void validateAuctionHasOutbidUsers(
            Session session,
            Long expectedAuctionId,
            long minBidCount,
            long expectedOutbidUserCount
    ) {
        List<OutbidAuctionSummary> summaries = auctionQueries.findAuctionsWithOutbidUsers(session, minBidCount);

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
            Session session,
            Long auctionId
    ) {
        List<AuctionPriceVelocity> quartiles = auctionQueries.findAuctionPriceVelocity(session, auctionId);

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
            Session session,
            Long auctionIdA,
            Long auctionIdB,
            long expectedSharedBidders
    ) {
        List<AuctionBidderOverlap> overlaps = auctionQueries.findCrossAuctionBidderOverlap(session);

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

