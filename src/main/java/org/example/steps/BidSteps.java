package org.example.steps;

import org.example.models.dto.BidRanks;
import org.example.models.dto.TopBidder;
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

    /* "do something" step: remove every bid (child table - clear before auctions). */
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
}

