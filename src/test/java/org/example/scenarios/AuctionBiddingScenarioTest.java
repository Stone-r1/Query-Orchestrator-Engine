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
import java.time.temporal.ChronoUnit;

/*
 * Orchestrator version - 6 test methods, all heavy analytical queries.
 * Created for solely for measuring performance
 */
@Test(groups = "Auctions/Bidding")
public class AuctionBiddingScenarioTest {

    private QueryRunner queryRunner;
    private AuctionSteps auctionSteps;
    private BidSteps bidSteps;

    private Long guitarAuctionId;
    private Long watchAuctionId;

    private final RetryPolicy awaitPolicy = RetryPolicy.of(10, Duration.ofMillis(500));

    @BeforeClass
    public void setUp() {
        queryRunner  = new QueryRunner();
        auctionSteps = new AuctionSteps();
        bidSteps     = new BidSteps();

        queryRunner.run(DbSource.MY_DB,
                bidSteps::clearBids,
                auctionSteps::clearAuctions
        );

        Auction guitar = new Auction(
                null, "Vintage Guitar", "1968 Fender Stratocaster", 500.0,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7),
                620.0, 1L, null);

        Auction watch = new Auction(
                null, "Rolex Submariner", "2020 model, unworn", 3000.0,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(5),
                3400.0, 2L, null);

        queryRunner.run(DbSource.MY_DB,
                session -> auctionSteps.insertAuction(session, guitar),
                session -> auctionSteps.insertAuction(session, watch)
        );

        guitarAuctionId = guitar.getAuctionId();
        watchAuctionId  = watch.getAuctionId();

        LocalDateTime baseHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(1);

        queryRunner.run(DbSource.MY_DB,
                session -> bidSteps.placeBid(session, new Bid(null, 10L, guitarAuctionId, 520.0,  baseHour.plusMinutes(5))),
                session -> bidSteps.placeBid(session, new Bid(null, 11L, guitarAuctionId, 560.0,  baseHour.plusMinutes(10))),
                session -> bidSteps.placeBid(session, new Bid(null, 10L, guitarAuctionId, 580.0,  baseHour.plusMinutes(15))),
                session -> bidSteps.placeBid(session, new Bid(null, 12L, guitarAuctionId, 590.0,  baseHour.plusMinutes(20))),
                session -> bidSteps.placeBid(session, new Bid(null, 11L, guitarAuctionId, 620.0,  baseHour.plusMinutes(25))),
                session -> bidSteps.placeBid(session, new Bid(null, 13L, watchAuctionId,  3100.0, baseHour.plusMinutes(5))),
                session -> bidSteps.placeBid(session, new Bid(null, 13L, watchAuctionId,  3200.0, baseHour.plusMinutes(10))),
                session -> bidSteps.placeBid(session, new Bid(null, 12L, watchAuctionId,  3250.0, baseHour.plusMinutes(15))),
                session -> bidSteps.placeBid(session, new Bid(null, 14L, watchAuctionId,  3400.0, baseHour.plusMinutes(20)))
        );
    }

    @Test(
            priority = 1,
            description = "Confirm all bids landed and per-auction totals are correct"
    )
    public void validateBidTotals() {
        queryRunner.await(DbSource.MY_DB, awaitPolicy,
                session -> auctionSteps.validateAuctionStats(session, guitarAuctionId, 5, 620.0)
        );
        queryRunner.await(DbSource.MY_DB, awaitPolicy,
                session -> auctionSteps.validateAuctionStats(session, watchAuctionId, 4, 3400.0)
        );
    }

    @Test(
            priority = 2,
            description = "Leaderboard validation: DENSE_RANK, spread from starting price, per-auction rank-1 user"
    )
    public void validateLeaderboards() {
        queryRunner.validateInParallel(DbSource.MY_DB, awaitPolicy,
                session -> auctionSteps.validateAuctionLeaderboard(session, guitarAuctionId, 3, 11L),
                session -> auctionSteps.validateAuctionLeaderboard(session, watchAuctionId, 3, 14L),
                session -> bidSteps.validateTopBidder(session, guitarAuctionId, 11L, 620.0),
                session -> bidSteps.validateTopBidder(session, watchAuctionId, 14L, 3400.0),
                session -> auctionSteps.validateAuctionAppearsForMinBid(session, 610.0, guitarAuctionId),
                session -> auctionSteps.validateAuctionAppearsForMinBid(session, 3300.0, watchAuctionId)
        );
    }

    @Test(
            priority = 3,
            description = "Bid analysis: competitive summaries, escalation chains, outbid detection"
    )
    public void validateBidAnalysis() {
        queryRunner.validateInParallel(DbSource.MY_DB, awaitPolicy,
                session -> bidSteps.validateCompetitiveBidSummary(session, guitarAuctionId, 5),
                session -> bidSteps.validateCompetitiveBidSummary(session, watchAuctionId, 4),
                session -> bidSteps.validateBidEscalationChain(session, guitarAuctionId, 5),
                session -> bidSteps.validateBidEscalationChain(session, watchAuctionId, 4),
                session -> auctionSteps.validateAuctionHasOutbidUsers(session, guitarAuctionId, 3L, 2L),
                session -> auctionSteps.validateAuctionHasOutbidUsers(session, watchAuctionId, 2L, 2L)
        );
    }

    @Test(
            priority = 4,
            description = "User profile validation: CTE-based aggregation across auctions, bid rank counts"
    )
    public void validateUserProfiles() {
        queryRunner.validateInParallel(DbSource.MY_DB, awaitPolicy,
                session -> bidSteps.validateBidderProfile(session, 12L, 2, 2),
                session -> bidSteps.validateBidderProfile(session, 11L, 1, 2),
                session -> bidSteps.validateBidderProfile(session, 10L, 1, 2),
                session -> bidSteps.validateBidderProfile(session, 14L, 1, 1),
                session -> bidSteps.validateBidRankCount(session, 10L, 2),
                session -> bidSteps.validateBidRankCount(session, 12L, 2)
        );
    }

    @Test(
            priority = 5,
            description = "Price metrics: NTILE quartile velocity and cross-auction bidder overlap"
    )
    public void validatePriceMetrics() {
        queryRunner.validateInParallel(DbSource.MY_DB, awaitPolicy,
                session -> auctionSteps.validateAuctionPriceVelocity(session, guitarAuctionId),
                session -> auctionSteps.validateAuctionPriceVelocity(session, watchAuctionId),
                session -> auctionSteps.validateCrossAuctionBidderOverlap(session, guitarAuctionId, watchAuctionId, 1L),
                session -> bidSteps.validateUserSpendRanking(session, 13L, 1),
                session -> bidSteps.validateUserSpendRanking(session, 12L, 2),
                session -> bidSteps.validateUserSpendRanking(session, 14L, 3)
        );
    }

    @Test(
            priority = 6,
            description = "Intensity and heatmap: DATE_TRUNC per-minute slots, cumulative windows, hourly activity buckets"
    )
    public void validateIntensityAndHeatmap() {
        queryRunner.validateInParallel(DbSource.MY_DB, awaitPolicy,
                session -> bidSteps.validateBidIntensity(session, guitarAuctionId, 5, 5L),
                session -> bidSteps.validateBidIntensity(session, watchAuctionId, 4, 4L),
                session -> bidSteps.validateUserActivityHeatmap(session, 10L, 1),
                session -> bidSteps.validateUserActivityHeatmap(session, 11L, 1),
                session -> bidSteps.validateUserActivityHeatmap(session, 12L, 1),
                session -> bidSteps.validateUserActivityHeatmap(session, 14L, 1)
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
