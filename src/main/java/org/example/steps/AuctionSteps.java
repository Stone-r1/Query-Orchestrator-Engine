package org.example.steps;

import org.example.models.dto.AuctionStats;
import org.example.models.entities.Auction;
import org.example.queries.AuctionQueries;
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
}

