package org.example.steps;

import org.example.models.dto.BidEscalationEntry;
import org.example.models.dto.BidIntensitySlot;
import org.example.models.dto.BidRanks;
import org.example.models.dto.BidderProfile;
import org.example.models.dto.CompetitiveBidSummary;
import org.example.models.dto.TopBidder;
import org.example.models.dto.UserActivityHeatmap;
import org.example.models.dto.UserSpendRanking;
import org.example.models.entities.Bid;
import org.example.queries.BidQueries;
import org.hibernate.Session;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class BidSteps {

    private final BidQueries bidQueries = new BidQueries();

    public void placeBid(
            Session session,
            Bid bid
    ) {
        bidQueries.insertBid(session, bid);
    }

    public void clearBids(
            Session session
    ) {
        bidQueries.deleteAllBids(session);
    }

    public void validateTopBidder(
            Session session,
            Long auctionId,
            Long expectedUserId,
            Double expectedAmount
    ) {
        List<TopBidder> leaderboard = bidQueries.findTopBidders(session, auctionId);

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
            Session session,
            Long userId,
            int expectedCount
    ) {
        List<BidRanks> ranks = bidQueries.getBidRanks(session, userId);

        assertThat(ranks)
                .as("user %d ranked bids", userId)
                .hasSize(expectedCount);
    }

    public void validateBidderProfile(
            Session session,
            Long userId,
            long expectedAuctionsParticipated,
            long expectedTotalBids
    ) {
        BidderProfile profile = bidQueries.getBidderProfile(session, userId);

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
            Session session,
            Long auctionId,
            int expectedBidCount
    ) {
        List<CompetitiveBidSummary> summary = bidQueries.findCompetitiveBidSummary(session, auctionId);

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
            Session session,
            Long userId,
            int expectedSlotCount
    ) {
        List<UserActivityHeatmap> heatmap = bidQueries.findUserActivityHeatmap(session, userId);

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
            Session session,
            Long auctionId,
            int expectedEntryCount
    ) {
        List<BidEscalationEntry> chain = bidQueries.findBidEscalationChain(session, auctionId);

        assertThat(chain)
                .as("auction %d escalation chain entry count", auctionId)
                .hasSize(expectedEntryCount);

        assertThat(chain.get(0).previousLeaderAmount())
                .as("first bid in auction %d must have no prior leader", auctionId)
                .isNull();

        assertThat(chain)
                .as("auction %d: all bids that have a prior leader must show positive escalation", auctionId)
                .filteredOn(e -> e.previousLeaderAmount() != null)
                .allSatisfy(e ->
                        assertThat(e.escalationAmount())
                                .as("escalation for bid %d must be positive", e.bidId())
                                .isGreaterThan(0.0)
                );
    }

    public void validateUserSpendRanking(
            Session session,
            Long expectedUserId,
            int expectedRank
    ) {
        List<UserSpendRanking> rankings = bidQueries.findUserRankingBySpend(session);

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
            Session session,
            Long auctionId,
            int expectedSlotCount,
            long expectedTotalBids
    ) {
        List<BidIntensitySlot> slots = bidQueries.findBidIntensityByAuction(session, auctionId);

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

