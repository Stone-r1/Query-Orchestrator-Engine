package org.example.steps.jdbc;

import org.example.models.dto.*;
import org.example.queries.jdbc.BidQueries;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class BidSteps {

    private final BidQueries bidQueries;

    public BidSteps(BidQueries bidQueries) {
        this.bidQueries = bidQueries;
    }

    public void placeBid(
            Long userId,
            Long auctionId,
            Double amount,
            LocalDateTime placedAt
    ) throws SQLException {
        bidQueries.insertBid(userId, auctionId, amount, placedAt);
    }

    public void clearBids() throws SQLException {
        bidQueries.deleteAllBids();
    }

    public void clearBidsForUsersFrom(long minUserId) throws SQLException {
        bidQueries.deleteBidsByUserIdFrom(minUserId);
    }

    public void validateTopBidder(
            Long auctionId,
            Long expectedUserId,
            Double expectedAmount
    ) throws SQLException {
        List<TopBidder> leaderboard = bidQueries.findTopBidders(auctionId);

        assertThat(leaderboard)
                .as("auction %d should have a top bidder", auctionId)
                .isNotEmpty();

        TopBidder leader = leaderboard.get(0);
        assertThat(leader.userId())
                .as("auction %d top bidder user", auctionId)
                .isEqualTo(expectedUserId);
        assertThat(leader.highestBid())
                .as("auction %d top bid amount", auctionId)
                .isEqualTo(expectedAmount);
    }

    public void validateBidRankCount(
            Long userId,
            int expectedCount
    ) throws SQLException {
        List<BidRanks> ranks = bidQueries.getBidRanks(userId);

        assertThat(ranks)
                .as("user %d ranked bids", userId)
                .hasSize(expectedCount);
    }

    public void validateBidderProfile(
            Long userId,
            long expectedAuctionsParticipated,
            long expectedTotalBids
    ) throws SQLException {
        BidderProfile profile = bidQueries.getBidderProfile(userId);

        assertThat(profile.auctionsParticipated())
                .as("user %d auctions participated", userId)
                .isEqualTo(expectedAuctionsParticipated);

        assertThat(profile.totalBidsPlaced())
                .as("user %d total bids placed", userId)
                .isEqualTo(expectedTotalBids);

        assertThat(profile.totalSpend())
                .as("user %d total spend must be positive", userId)
                .isGreaterThan(0.0);
    }

    public void validateCompetitiveBidSummary(
            Long auctionId,
            int expectedBidCount
    ) throws SQLException {
        List<CompetitiveBidSummary> summary = bidQueries.findCompetitiveBidSummary(auctionId);

        assertThat(summary)
                .as("auction %d competitive summary bid count", auctionId)
                .hasSize(expectedBidCount);

        assertThat(summary)
                .as("auction %d: rank-1 bid must have zero gap to leader", auctionId)
                .filteredOn(b -> b.rankInAuction() == 1)
                .allSatisfy(b ->
                        assertThat(b.gapToLeader())
                                .as("rank-1 bid gap must be 0")
                                .isEqualTo(0.0)
                );

        assertThat(summary)
                .as("auction %d: non-leader bids must have positive gap", auctionId)
                .filteredOn(b -> b.rankInAuction() > 1)
                .allSatisfy(b ->
                        assertThat(b.gapToLeader())
                                .as("non-leader bid gap must be > 0")
                                .isGreaterThan(0.0)
                );
    }

    public void validateUserActivityHeatmap(
            Long userId,
            int expectedSlotCount
    ) throws SQLException {
        List<UserActivityHeatmap> heatmap = bidQueries.findUserActivityHeatmap(userId);

        assertThat(heatmap)
                .as("user %d heatmap slot count", userId)
                .hasSize(expectedSlotCount);

        assertThat(heatmap)
                .as("user %d: all heatmap slots must have positive spend", userId)
                .allSatisfy(slot ->
                        assertThat(slot.totalSpend())
                                .as("slot hour=%d spend", slot.hourOfDay())
                                .isGreaterThan(0.0)
                );
    }

    public void validateBidEscalationChain(
            Long auctionId,
            int expectedEntryCount
    ) throws SQLException {
        List<BidEscalationEntry> chain = bidQueries.findBidEscalationChain(auctionId);

        assertThat(chain)
                .as("auction %d escalation chain entry count", auctionId)
                .hasSize(expectedEntryCount);

        assertThat(chain.get(0).previousLeaderAmount())
                .as("first bid in auction %d must have no prior leader", auctionId)
                .isNull();

        assertThat(chain)
                .as("auction %d: all bids with a prior leader must show positive escalation", auctionId)
                .filteredOn(e -> e.previousLeaderAmount() != null)
                .allSatisfy(e ->
                        assertThat(e.escalationAmount())
                                .as("escalation for bid %d must be positive", e.bidId())
                                .isGreaterThan(0.0)
                );
    }

    public void validateUserSpendRanking(
            long minUserId,
            Long expectedUserId,
            int expectedRank
    ) throws SQLException {
        List<UserSpendRanking> rankings = bidQueries.findUserRankingBySpend(minUserId);

        assertThat(rankings)
                .as("spend ranking must be non-empty")
                .isNotEmpty();

        UserSpendRanking userEntry = rankings.stream()
                .filter(r -> r.userId().equals(expectedUserId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "User %d not found in spend ranking".formatted(expectedUserId)
                ));

        assertThat(userEntry.spendRank())
                .as("user %d expected spend rank", expectedUserId)
                .isEqualTo(expectedRank);

        assertThat(userEntry.totalSpend())
                .as("user %d total spend must be positive", expectedUserId)
                .isGreaterThan(0.0);
    }

    public void validateBidIntensity(
            Long auctionId,
            int expectedSlotCount,
            long expectedTotalBids
    ) throws SQLException {
        List<BidIntensitySlot> slots = bidQueries.findBidIntensityByAuction(auctionId);

        assertThat(slots)
                .as("auction %d bid intensity slot count", auctionId)
                .hasSize(expectedSlotCount);

        assertThat(slots.get(slots.size() - 1).cumulativeBidCount())
                .as("auction %d cumulative bid count in last slot must equal total bids", auctionId)
                .isEqualTo(expectedTotalBids);

        assertThat(slots)
                .as("auction %d: every intensity slot must have a positive max bid", auctionId)
                .allSatisfy(slot ->
                        assertThat(slot.maxBidInSlot())
                                .as("max bid in slot %s", slot.minute())
                                .isGreaterThan(0.0)
                );
    }
}
