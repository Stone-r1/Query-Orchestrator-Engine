package org.example.steps;

import org.example.models.dto.AuctionStats;
import org.example.models.entities.Auction;
import org.example.queries.AuctionQueries;
import org.hibernate.Session;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Example steps. A step's contract: receive the session, hand it to the page (queries),
 * read something back, and validate it. The session is given by QueryRunner - the step
 * never opens, commits, or closes it. Replace/extend these with your own steps.
 */
public class AuctionSteps {

    private final AuctionQueries auctionQueries = new AuctionQueries();

    /* "do something" step: hand the session to the page and persist. */
    public void insertAuction(
            Session session,
            Auction auction
    ) {
        auctionQueries.insertAuction(session, auction);
    }

    /* "do something" step: remove every auction (parent table - clear after bids). */
    public void clearAuctions(
            Session session
    ) {
        auctionQueries.deleteAllAuctions(session);
    }

    /* "validate" step: read aggregated stats via the page, then assert the expectation. */
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

    /* "validate" step: confirm the auction shows up among those with maxBid >= minBid. */
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
}

