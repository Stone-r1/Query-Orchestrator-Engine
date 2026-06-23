package org.example.scenarios;

import org.example.models.entities.Auction;
import org.example.models.entities.Bid;
import org.example.steps.AuctionSteps;
import org.example.steps.BidSteps;
import org.example.util.DbSource;
import org.example.util.QueryRunner;
import org.example.util.RetryPolicy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.LocalDateTime;

/*
 * Just an example of usage, rather than full E2E flow
 */
@Test(groups = "Auctions/Bidding")
public class AuctionBiddingScenarioTest {

    private QueryRunner queryRunner;
    private AuctionSteps auctionSteps;
    private BidSteps bidSteps;
    private Auction auction;

    /*
     * Poll up to 10 times, 500ms apart - used by both await() and the parallel validations.
     */
    private final RetryPolicy awaitPolicy = RetryPolicy.of(
            10,
            Duration.ofMillis(500)
    );

    @BeforeClass
    public void setUp() {
        queryRunner = new QueryRunner();
        auctionSteps = new AuctionSteps();
        bidSteps = new BidSteps();

        queryRunner.run(DbSource.MY_DB,
                bidSteps::clearBids,
                auctionSteps::clearAuctions
        );

        auction = new Auction(
                null, "Vintage Guitar", "1968 Fender Stratocaster", 500.0,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7),
                600.0, 1L, null);

        queryRunner.run(DbSource.MY_DB,
                session -> auctionSteps.insertAuction(session, auction)
        );
    }

    @Test(
            priority = 1,
            description = "Two users bid; wait for both bids to land"
    )
    public void placeBids() {
         Long auctionId = auction.getAuctionId();

        queryRunner.run(DbSource.MY_DB,
                session -> bidSteps.placeBid(session, new Bid(null, 10L, auctionId, 550.0, LocalDateTime.now())),
                session -> bidSteps.placeBid(session, new Bid(null, 11L, auctionId, 600.0, LocalDateTime.now()))
        );

        queryRunner.await(DbSource.MY_DB, awaitPolicy,
                session -> auctionSteps.validateAuctionStats(session, auctionId, 2, 600.0)
        );
    }

    @Test(
            priority = 2,
            description = "Validate the bids were placed"
    )
    public void validateBidsPlaced() {
        Long auctionId = auction.getAuctionId();

        queryRunner.validateInParallel(DbSource.MY_DB, awaitPolicy,
                session -> auctionSteps.validateAuctionStats(session, auctionId, 2, 600.0),
                session -> bidSteps.validateTopBidder(session, auctionId, 11L, 600.0),
                session -> bidSteps.validateBidRankCount(session, 10L, 1),
                session -> auctionSteps.validateAuctionAppearsForMinBid(session, 590.0, auctionId)
        );
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (queryRunner == null) {
            return;
        }

        queryRunner.run(DbSource.MY_DB,
                bidSteps::clearBids,
                auctionSteps::clearAuctions
        );
    }
}

