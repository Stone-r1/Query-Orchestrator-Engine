package org.example.queries.jdbc;

import org.example.models.dto.*;
import org.example.util.ConnectionProvider;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@SuppressWarnings("unchecked")
public class AuctionQueries {

    private static final String INSERT_AUCTION = """
            INSERT INTO auction (item_name, item_description, starting_price, start_date, end_date, max_bid, seller_id, winner_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String DELETE_ALL_AUCTIONS = "DELETE FROM auction";

    private static final String GET_AUCTION_STATS = """
            SELECT a.auction_id,
                   a.item_name,
                   COUNT(b.bid_id),
                   MAX(b.amount)
            FROM auction a
            LEFT JOIN bid b ON b.auction_id = a.auction_id
            WHERE a.auction_id = ?
            GROUP BY a.auction_id, a.item_name
            """;

    private static final String FIND_BY_MIN_MAX_BID = """
            SELECT auction_id FROM auction WHERE max_bid >= ?
            """;

    public long insertAuction(
            String itemName,
            String itemDescription,
            Double startingPrice,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Double maxBid,
            Long sellerId,
            Long winnerId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(INSERT_AUCTION, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, itemName);
            stmt.setString(2, itemDescription);
            stmt.setDouble(3, startingPrice);
            stmt.setTimestamp(4, Timestamp.valueOf(startDate));
            stmt.setTimestamp(5, Timestamp.valueOf(endDate));
            stmt.setDouble(6, maxBid);
            stmt.setLong(7, sellerId);
            if (winnerId != null) {
                stmt.setLong(8, winnerId);
            } else {
                stmt.setNull(8, Types.BIGINT);
            }

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Insert returned no generated key");
            }
        }
    }

    public void deleteAllAuctions() throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(DELETE_ALL_AUCTIONS);
        }
    }

    private static final String DELETE_AUCTION_BY_ID =
            "DELETE FROM auction WHERE auction_id = ?";

    public void deleteAuctionById(long auctionId) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(DELETE_AUCTION_BY_ID)) {
            stmt.setLong(1, auctionId);
            stmt.executeUpdate();
        }
    }

    public AuctionStats getAuctionStats(
            Long auctionId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(GET_AUCTION_STATS)) {

            stmt.setLong(1, auctionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No auction found with id: " + auctionId);
                }
                return new AuctionStats(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getObject(4) != null ? rs.getDouble(4) : null
                );
            }
        }
    }

    public List<Long> findAuctionIdsWithMinMaxBid(
            Double minBid
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_BY_MIN_MAX_BID)) {

            stmt.setDouble(1, minBid);

            try (ResultSet rs = stmt.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
                return ids;
            }
        }
    }

    private static final String FIND_AUCTION_LEADERBOARD = """
            SELECT
                b.auction_id,
                b.user_id,
                MAX(b.amount) AS top_bid,
                DENSE_RANK() OVER (ORDER BY MAX(b.amount) DESC) AS bid_rank,
                COUNT(b.bid_id) AS bid_count,
                MAX(b.amount) - a.starting_price AS spread
            FROM bid b
            JOIN auction a ON a.auction_id = b.auction_id
            WHERE b.auction_id = ?
            GROUP BY b.auction_id, b.user_id, a.starting_price
            ORDER BY bid_rank
            """;

    private static final String FIND_AUCTIONS_WITH_OUTBID_USERS = """
            SELECT
                a.auction_id,
                a.item_name,
                COUNT(b.bid_id) AS total_bids,
                COUNT(DISTINCT outbid.user_id) AS outbid_user_count,
                MAX(b.amount) AS current_max_bid,
                a.starting_price
            FROM auction a
            JOIN bid b ON b.auction_id = a.auction_id
            JOIN (
                SELECT b2.auction_id, b2.user_id
                FROM bid b2
                GROUP BY b2.auction_id, b2.user_id
                HAVING MAX(b2.amount) < (
                    SELECT MAX(b3.amount)
                    FROM bid b3
                    WHERE b3.auction_id = b2.auction_id
                )
            ) outbid ON outbid.auction_id = a.auction_id
            GROUP BY a.auction_id, a.item_name, a.starting_price
            HAVING COUNT(b.bid_id) >= ?
            ORDER BY outbid_user_count DESC
            """;

    public List<AuctionLeaderboardEntry> findAuctionLeaderboard(
            Long auctionId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_AUCTION_LEADERBOARD)) {

            stmt.setLong(1, auctionId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<AuctionLeaderboardEntry> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new AuctionLeaderboardEntry(
                            rs.getLong(1),
                            rs.getLong(2),
                            rs.getDouble(3),
                            rs.getInt(4),
                            rs.getLong(5),
                            rs.getDouble(6)
                    ));
                }
                return results;
            }
        }
    }

    public List<OutbidAuctionSummary> findAuctionsWithOutbidUsers(
            long minBidCount
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_AUCTIONS_WITH_OUTBID_USERS)) {

            stmt.setLong(1, minBidCount);

            try (ResultSet rs = stmt.executeQuery()) {
                List<OutbidAuctionSummary> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new OutbidAuctionSummary(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getLong(3),
                            rs.getLong(4),
                            rs.getDouble(5),
                            rs.getDouble(6)
                    ));
                }
                return results;
            }
        }
    }

    private static final String FIND_AUCTION_PRICE_VELOCITY = """
            WITH quartiled AS (
                SELECT
                    b.auction_id,
                    b.amount,
                    NTILE(4) OVER (ORDER BY b.placed_at) AS quartile
                FROM bid b
                WHERE b.auction_id = ?
            ),
            quartile_stats AS (
                SELECT
                    q.auction_id,
                    q.quartile,
                    COUNT(*) AS bid_count,
                    MAX(q.amount) AS max_bid
                FROM quartiled q
                GROUP BY q.auction_id, q.quartile
            )
            SELECT
                qs.auction_id,
                qs.quartile,
                qs.bid_count,
                qs.max_bid,
                qs.max_bid - COALESCE(
                    LAG(qs.max_bid) OVER (ORDER BY qs.quartile),
                    qs.max_bid
                ) AS price_gain
            FROM quartile_stats qs
            ORDER BY qs.quartile
            """;

    private static final String FIND_CROSS_AUCTION_BIDDER_OVERLAP = """
            WITH auction_bidders AS (
                SELECT DISTINCT auction_id, user_id
                FROM bid
            ),
            pairs AS (
                SELECT
                    a1.auction_id AS auction_a,
                    a2.auction_id AS auction_b,
                    a1.user_id
                FROM auction_bidders a1
                JOIN auction_bidders a2
                ON a1.user_id = a2.user_id
                AND a1.auction_id < a2.auction_id
            )
            SELECT
                p.auction_a,
                p.auction_b,
                COUNT(DISTINCT p.user_id) AS shared_bidders
            FROM pairs p
            GROUP BY p.auction_a, p.auction_b
            ORDER BY shared_bidders DESC, p.auction_a, p.auction_b
            """;

    public List<AuctionPriceVelocity> findAuctionPriceVelocity(
            Long auctionId
    ) throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_AUCTION_PRICE_VELOCITY)) {

            stmt.setLong(1, auctionId);

            try (ResultSet rs = stmt.executeQuery()) {
                List<AuctionPriceVelocity> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new AuctionPriceVelocity(
                            rs.getLong(1),
                            rs.getInt(2),
                            rs.getLong(3),
                            rs.getDouble(4),
                            rs.getDouble(5)
                    ));
                }
                return results;
            }
        }
    }

    public List<AuctionBidderOverlap> findCrossAuctionBidderOverlap() throws SQLException {
        try (Connection connection = ConnectionProvider.get();
             PreparedStatement stmt = connection.prepareStatement(FIND_CROSS_AUCTION_BIDDER_OVERLAP)) {

            try (ResultSet rs = stmt.executeQuery()) {
                List<AuctionBidderOverlap> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new AuctionBidderOverlap(
                            rs.getLong(1),
                            rs.getLong(2),
                            rs.getLong(3)
                    ));
                }
                return results;
            }
        }
    }
}
