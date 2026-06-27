package org.example.scenarios;

import org.example.queries.jdbc.AuctionQueries;
import org.example.queries.jdbc.BidQueries;
import org.example.steps.jdbc.AuctionSteps;
import org.example.steps.jdbc.BidSteps;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;


@Test(groups = "Auctions/Bidding")
public class AuctionBiddingJdbcScenarioTest {

    private AuctionSteps auctionSteps;
    private BidSteps bidSteps;

    private long guitarAuctionId;
    private long watchAuctionId;

    // Test bidders live above this floor so cleanup never touches real bid data.
    private static final long TEST_USER_ID_FLOOR = 1_000_000L;

    @BeforeClass
    public void setUp() throws SQLException {
        auctionSteps = new AuctionSteps(new AuctionQueries());
        bidSteps = new BidSteps(new BidQueries());

        guitarAuctionId = auctionSteps.insertAuction(
                "Vintage Guitar", "1968 Fender Stratocaster", 500.0,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7),
                620.0, 1L, null);

        watchAuctionId = auctionSteps.insertAuction(
                "Rolex Submariner", "2020 model, unworn", 3000.0,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(5),
                3400.0, 2L, null);

        LocalDateTime baseHour = LocalDateTime.now()
                .truncatedTo(ChronoUnit.HOURS)
                .minusHours(1);

        bidSteps.placeBid(1_000_010L, guitarAuctionId, 520.0, baseHour.plusMinutes(5));
        bidSteps.placeBid(1_000_011L, guitarAuctionId, 560.0, baseHour.plusMinutes(10));
        bidSteps.placeBid(1_000_010L, guitarAuctionId, 580.0, baseHour.plusMinutes(15));
        bidSteps.placeBid(1_000_012L, guitarAuctionId, 590.0, baseHour.plusMinutes(20));
        bidSteps.placeBid(1_000_011L, guitarAuctionId, 620.0, baseHour.plusMinutes(25));
        bidSteps.placeBid(1_000_013L, watchAuctionId, 3100.0, baseHour.plusMinutes(5));
        bidSteps.placeBid(1_000_013L, watchAuctionId, 3200.0, baseHour.plusMinutes(10));
        bidSteps.placeBid(1_000_012L, watchAuctionId, 3250.0, baseHour.plusMinutes(15));
        bidSteps.placeBid(1_000_014L, watchAuctionId, 3400.0, baseHour.plusMinutes(20));
    }

    @Test(
            priority = 1,
            description = "Confirm all bids landed and per-auction totals are correct"
    )
    public void validateBidTotals() throws SQLException {
        auctionSteps.validateAuctionStats(guitarAuctionId, 5, 620.0);
        auctionSteps.validateAuctionStats(watchAuctionId, 4, 3400.0);
    }

    @Test(
            priority = 2,
            description = "Leaderboard validation: DENSE_RANK, spread from starting price, per-auction rank-1 user"
    )
    public void validateLeaderboards() throws SQLException {
        auctionSteps.validateAuctionLeaderboard(guitarAuctionId, 3, 1_000_011L);
        auctionSteps.validateAuctionLeaderboard(watchAuctionId, 3, 1_000_014L);
        bidSteps.validateTopBidder(guitarAuctionId, 1_000_011L, 620.0);
        bidSteps.validateTopBidder(watchAuctionId, 1_000_014L, 3400.0);
        auctionSteps.validateAuctionAppearsForMinBid(610.0, guitarAuctionId);
        auctionSteps.validateAuctionAppearsForMinBid(3300.0, watchAuctionId);
    }

    @Test(
            priority = 3,
            description = "Bid analysis: competitive summaries, escalation chains, outbid detection"
    )
    public void validateBidAnalysis() throws SQLException {
        bidSteps.validateCompetitiveBidSummary(guitarAuctionId, 5);
        bidSteps.validateCompetitiveBidSummary(watchAuctionId, 4);
        bidSteps.validateBidEscalationChain(guitarAuctionId, 5);
        bidSteps.validateBidEscalationChain(watchAuctionId, 4);
        auctionSteps.validateAuctionHasOutbidUsers(guitarAuctionId, 3L, 2L);
        auctionSteps.validateAuctionHasOutbidUsers(watchAuctionId, 2L, 2L);
    }

    @Test(
            priority = 4,
            description = "User profile validation: CTE-based aggregation across auctions, bid rank counts"
    )
    public void validateUserProfiles() throws SQLException {
        bidSteps.validateBidderProfile(1_000_012L, 2, 2);
        bidSteps.validateBidderProfile(1_000_011L, 1, 2);
        bidSteps.validateBidderProfile(1_000_010L, 1, 2);
        bidSteps.validateBidderProfile(1_000_014L, 1, 1);
        bidSteps.validateBidRankCount(1_000_010L, 2);
        bidSteps.validateBidRankCount(1_000_012L, 2);
    }

    @Test(
            priority = 5,
            description = "Price metrics: NTILE quartile velocity and cross-auction bidder overlap"
    )
    public void validatePriceMetrics() throws SQLException {
        auctionSteps.validateAuctionPriceVelocity(guitarAuctionId);
        auctionSteps.validateAuctionPriceVelocity(watchAuctionId);
        auctionSteps.validateCrossAuctionBidderOverlap(guitarAuctionId, watchAuctionId, 1L);
        bidSteps.validateUserSpendRanking(TEST_USER_ID_FLOOR, 1_000_013L, 1);
        bidSteps.validateUserSpendRanking(TEST_USER_ID_FLOOR, 1_000_012L, 2);
        bidSteps.validateUserSpendRanking(TEST_USER_ID_FLOOR, 1_000_014L, 3);
    }

    @Test(
            priority = 6,
            description = "Intensity and heatmap: DATE_TRUNC per-minute slots, cumulative windows, hourly activity buckets"
    )
    public void validateIntensityAndHeatmap() throws SQLException {
        bidSteps.validateBidIntensity(guitarAuctionId, 5, 5L);
        bidSteps.validateBidIntensity(watchAuctionId, 4, 4L);
        bidSteps.validateUserActivityHeatmap(1_000_010L, 1);
        bidSteps.validateUserActivityHeatmap(1_000_011L, 1);
        bidSteps.validateUserActivityHeatmap(1_000_012L, 1);
        bidSteps.validateUserActivityHeatmap(1_000_014L, 1);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws SQLException {
        bidSteps.clearBidsForUsersFrom(TEST_USER_ID_FLOOR);
        auctionSteps.clearAuction(guitarAuctionId);
        auctionSteps.clearAuction(watchAuctionId);
    }
}
