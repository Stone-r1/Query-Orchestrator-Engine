package org.example.queries.jdbc;

import org.example.models.dto.*;
import org.example.util.ConnectionProvider;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@SuppressWarnings("unchecked")
public class BidQueries {

    private static final String INSERT_BID = """
            INSERT INTO bid (user_id, auction_id, amount, placed_at)
            VALUES (?, ?, ?, ?)
            """;

    private static final String DELETE_ALL_BIDS = "DELETE FROM bid";

    private static final String FIND_TOP_BIDDERS = """
            SELECT user_id, MAX(amount)
            FROM bid
            WHERE auction_id = ?
            GROUP BY user_id
            ORDER BY MAX(amount) DESC
            """;

    private static final String GET_BID_RANKS = """
            SELECT auction_id,
                   amount,
                   placed_at,
                   RANK() OVER (
                       PARTITION BY auction_id
                       ORDER BY amount DESC
                   ) AS bid_rank
            FROM bid
            WHERE user_id = ?
            """;

    public void insertBid(
            Long userId,
            Long auctionId,
            Double amount,
            LocalDateTime placedAt
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(INSERT_BID)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, auctionId);
            stmt.setDouble(3, amount);
            stmt.setTimestamp(4, Timestamp.valueOf(placedAt));
            stmt.executeUpdate();
        }
    }

    public void deleteAllBids() throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(DELETE_ALL_BIDS);
        }
    }

    private static final String DELETE_BIDS_BY_USER_FROM =
            "DELETE FROM bid WHERE user_id >= ?";

    public void deleteBidsByUserIdFrom(long minUserId) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(DELETE_BIDS_BY_USER_FROM)) {
            stmt.setLong(1, minUserId);
            stmt.executeUpdate();
        }
    }

    public List<TopBidder> findTopBidders(
            Long auctionId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_TOP_BIDDERS)) {

            stmt.setLong(1, auctionId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<TopBidder> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new TopBidder(rs.getLong(1), rs.getDouble(2)));
                }
                return results;
            }
        }
    }

    public List<BidRanks> getBidRanks(
            Long userId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(GET_BID_RANKS)) {

            stmt.setLong(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<BidRanks> results = new ArrayList<>();
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp(3);
                    results.add(new BidRanks(
                            rs.getLong(1),
                            rs.getDouble(2),
                            ts != null ? ts.toLocalDateTime() : null,
                            rs.getInt(4)
                    ));
                }
                return results;
            }
        }
    }

    private static final String GET_BIDDER_PROFILE = """
            WITH per_auction AS (
                SELECT
                    b.auction_id,
                    COUNT(b.bid_id) AS bids_in_auction,
                    SUM(b.amount) AS spend_in_auction
                FROM bid b
                WHERE b.user_id = ?
                GROUP BY b.auction_id
            )
            SELECT
                ? AS user_id,
                SUM(pa.bids_in_auction) AS total_bids,
                COUNT(pa.auction_id) AS auctions_participated,
                SUM(pa.spend_in_auction) AS total_spend,
                AVG(pa.spend_in_auction) AS avg_bid_per_auction
            FROM per_auction pa
            """;

    private static final String FIND_COMPETITIVE_BID_SUMMARY = """
            SELECT
                b.bid_id,
                b.user_id,
                b.amount,
                RANK() OVER (ORDER BY b.amount DESC) AS rank_in_auction,
                (SELECT MAX(b2.amount) FROM bid b2
                 WHERE b2.auction_id = b.auction_id) - b.amount AS gap_to_leader,
                LAG(b.amount) OVER (ORDER BY b.placed_at) AS previous_bid_amount
            FROM bid b
            WHERE b.auction_id = ?
            ORDER BY b.placed_at
            """;

    public BidderProfile getBidderProfile(
            Long userId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(GET_BIDDER_PROFILE)) {

            stmt.setLong(1, userId);
            stmt.setLong(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No bids found for user: " + userId);
                }
                return new BidderProfile(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        rs.getDouble(4),
                        rs.getDouble(5)
                );
            }
        }
    }

    public List<CompetitiveBidSummary> findCompetitiveBidSummary(
            Long auctionId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_COMPETITIVE_BID_SUMMARY)) {

            stmt.setLong(1, auctionId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<CompetitiveBidSummary> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new CompetitiveBidSummary(
                            rs.getLong(1),
                            rs.getLong(2),
                            rs.getDouble(3),
                            rs.getInt(4),
                            rs.getDouble(5),
                            rs.getObject(6) != null ? rs.getDouble(6) : null
                    ));
                }
                return results;
            }
        }
    }

    private static final String FIND_USER_ACTIVITY_HEATMAP = """
            WITH hourly_bids AS (
                SELECT
                    b.user_id,
                    EXTRACT(HOUR FROM b.placed_at)::INT AS hour_of_day,
                    COUNT(b.bid_id) AS bid_count,
                    SUM(b.amount) AS total_spend
                FROM bid b
                WHERE b.user_id = ?
                GROUP BY b.user_id, EXTRACT(HOUR FROM b.placed_at)::INT
            )
            SELECT
                hb.user_id,
                hb.hour_of_day,
                SUM(hb.bid_count) AS total_bids,
                SUM(hb.total_spend) AS total_spend
            FROM hourly_bids hb
            GROUP BY hb.user_id, hb.hour_of_day
            ORDER BY hb.hour_of_day
            """;

    private static final String FIND_BID_ESCALATION_CHAIN = """
            SELECT
                b.bid_id,
                b.user_id,
                b.amount,
                (
                    SELECT MAX(b2.amount)
                    FROM bid b2
                    WHERE b2.auction_id = b.auction_id
                      AND b2.placed_at < b.placed_at
                      AND b2.user_id  != b.user_id
                ) AS previous_leader_amount,
                b.amount - COALESCE(
                    (
                        SELECT MAX(b2.amount)
                        FROM bid b2
                        WHERE b2.auction_id = b.auction_id
                          AND b2.placed_at < b.placed_at
                          AND b2.user_id  != b.user_id
                    ),
                    0
                ) AS escalation_amount
            FROM bid b
            WHERE b.auction_id = ?
            ORDER BY b.placed_at
            """;

    private static final String FIND_USER_RANKING_BY_SPEND = """
            WITH user_totals AS (
                SELECT
                    b.user_id,
                    SUM(b.amount) AS total_spend,
                    COUNT(DISTINCT b.auction_id) AS auctions_participated
                FROM bid b
                WHERE b.user_id >= ?
                GROUP BY b.user_id
            )
            SELECT
                ut.user_id,
                ut.total_spend,
                ut.auctions_participated,
                DENSE_RANK() OVER (ORDER BY ut.total_spend DESC) AS spend_rank
            FROM user_totals ut
            ORDER BY spend_rank
            """;

    private static final String FIND_BID_INTENSITY_BY_AUCTION = """
            SELECT
                DATE_TRUNC('minute', b.placed_at) AS minute,
                COUNT(b.bid_id) AS bids_in_slot,
                MAX(b.amount) AS max_bid_in_slot,
                SUM(COUNT(b.bid_id)) OVER (ORDER BY DATE_TRUNC('minute', b.placed_at)) AS cumulative_bids
            FROM bid b
            WHERE b.auction_id = ?
            GROUP BY DATE_TRUNC('minute', b.placed_at)
            ORDER BY minute
            """;

    public List<UserActivityHeatmap> findUserActivityHeatmap(
            Long userId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_USER_ACTIVITY_HEATMAP)) {

            stmt.setLong(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<UserActivityHeatmap> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new UserActivityHeatmap(
                            rs.getLong(1),
                            rs.getInt(2),
                            rs.getLong(3),
                            rs.getDouble(4)
                    ));
                }
                return results;
            }
        }
    }

    public List<BidEscalationEntry> findBidEscalationChain(
            Long auctionId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_BID_ESCALATION_CHAIN)) {

            stmt.setLong(1, auctionId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<BidEscalationEntry> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new BidEscalationEntry(
                            rs.getLong(1),
                            rs.getLong(2),
                            rs.getDouble(3),
                            rs.getObject(4) != null ? rs.getDouble(4) : null,
                            rs.getDouble(5)
                    ));
                }
                return results;
            }
        }
    }

    public List<UserSpendRanking> findUserRankingBySpend(
            long minUserId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_USER_RANKING_BY_SPEND)) {

            stmt.setLong(1, minUserId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<UserSpendRanking> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new UserSpendRanking(
                            rs.getLong(1),
                            rs.getDouble(2),
                            rs.getLong(3),
                            rs.getInt(4)
                    ));
                }
                return results;
            }
        }
    }

    public List<BidIntensitySlot> findBidIntensityByAuction(
            Long auctionId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_BID_INTENSITY_BY_AUCTION)) {

            stmt.setLong(1, auctionId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<BidIntensitySlot> results = new ArrayList<>();
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp(1);
                    results.add(new BidIntensitySlot(
                            ts != null ? ts.toLocalDateTime() : null,
                            rs.getLong(2),
                            rs.getDouble(3),
                            rs.getLong(4)
                    ));
                }
                return results;
            }
        }
    }
}
