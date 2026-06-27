package org.example.queries.queryRunner;


import org.example.models.dto.BidEscalationEntry;
import org.example.models.dto.BidIntensitySlot;
import org.example.models.dto.BidRanks;
import org.example.models.dto.BidderProfile;
import org.example.models.dto.CompetitiveBidSummary;
import org.example.models.dto.TopBidder;
import org.example.models.dto.UserActivityHeatmap;
import org.example.models.dto.UserSpendRanking;
import org.example.models.entities.Bid;
import org.example.util.HelperFunctions;
import org.hibernate.Session;

import java.util.List;


@SuppressWarnings("unchecked")
public class BidQueries {

    public void insertBid(
            Session session,
            Bid bid
    ) {
        session.persist(bid);
    }

    public void deleteAllBids(
            Session session
    ) {
        session.createMutationQuery("DELETE FROM Bid").executeUpdate();
    }

    public void deleteBidsByUserIdFrom(
            Session session,
            long minUserId
    ) {
        session.createMutationQuery("DELETE FROM Bid b WHERE b.userId >= :minUserId")
                .setParameter("minUserId", minUserId)
                .executeUpdate();
    }

    public List<TopBidder> findTopBidders(
            Session session,
            Long auctionId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            SELECT 
                b.user_id, 
                MAX(b.amount)
            FROM bid b
            WHERE b.auction_id = :auctionId
            GROUP BY b.user_id
            ORDER BY MAX(b.amount) DESC
        """)
                .setParameter("auctionId", auctionId)
                .getResultList();

        return rows.stream()
                .map(r -> new TopBidder(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).doubleValue()
                ))
                .toList();
    }

    public List<BidRanks> getBidRanks(
            Session session,
            Long userId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            SELECT b.auction_id,
                b.amount,
                b.placed_at,
                RANK() OVER (
                    PARTITION BY b.auction_id
                    ORDER BY b.amount DESC
                ) AS bid_rank
            FROM bid b
            WHERE b.user_id = :userId
        """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream()
                .map(r -> new BidRanks(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).doubleValue(),
                        HelperFunctions.toLocalDateTime(r[2]),
                        ((Number) r[3]).intValue()
                ))
                .toList();
    }

    public BidderProfile getBidderProfile(
            Session session,
            Long userId
    ) {
        Object[] r = (Object[]) session.createNativeQuery("""
            WITH per_auction AS (
                SELECT
                    b.auction_id,
                    COUNT(b.bid_id) AS bids_in_auction,
                    SUM(b.amount) AS spend_in_auction
                FROM bid b
                WHERE b.user_id = :userId
                GROUP BY b.auction_id
            )
            SELECT
                :userId AS user_id,
                SUM(pa.bids_in_auction) AS total_bids,
                COUNT(pa.auction_id) AS auctions_participated,
                SUM(pa.spend_in_auction) AS total_spend,
                AVG(pa.spend_in_auction) AS avg_bid_per_auction
            FROM per_auction pa
        """)
                .setParameter("userId", userId)
                .getSingleResult();

        return new BidderProfile(
                ((Number) r[0]).longValue(),
                ((Number) r[1]).longValue(),
                ((Number) r[2]).longValue(),
                ((Number) r[3]).doubleValue(),
                ((Number) r[4]).doubleValue()
        );
    }

    public List<CompetitiveBidSummary> findCompetitiveBidSummary(
            Session session,
            Long auctionId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            SELECT
                b.bid_id,
                b.user_id,
                b.amount,
                RANK() OVER (ORDER BY b.amount DESC) AS rank_in_auction,
                (SELECT MAX(b2.amount) FROM bid b2
                 WHERE b2.auction_id = b.auction_id) - b.amount AS gap_to_leader,
                LAG(b.amount) OVER (ORDER BY b.placed_at) AS previous_bid_amount
            FROM bid b
            WHERE b.auction_id = :auctionId
            ORDER BY b.placed_at
        """)
                .setParameter("auctionId", auctionId)
                .getResultList();

        return rows.stream()
                .map(r -> new CompetitiveBidSummary(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).doubleValue(),
                        ((Number) r[3]).intValue(),
                        ((Number) r[4]).doubleValue(),
                        r[5] != null ? ((Number) r[5]).doubleValue() : null
                ))
                .toList();
    }

    public List<UserActivityHeatmap> findUserActivityHeatmap(
            Session session,
            Long userId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            WITH hourly_bids AS (
                SELECT
                    b.user_id,
                    EXTRACT(HOUR FROM b.placed_at)::INT AS hour_of_day,
                    COUNT(b.bid_id) AS bid_count,
                    SUM(b.amount) AS total_spend
                FROM bid b
                WHERE b.user_id = :userId
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
        """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream()
                .map(r -> new UserActivityHeatmap(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).intValue(),
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).doubleValue()
                ))
                .toList();
    }

    public List<BidEscalationEntry> findBidEscalationChain(
            Session session,
            Long auctionId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
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
            WHERE b.auction_id = :auctionId
            ORDER BY b.placed_at
        """)
                .setParameter("auctionId", auctionId)
                .getResultList();

        return rows.stream()
                .map(r -> new BidEscalationEntry(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).doubleValue(),
                        r[3] != null ? ((Number) r[3]).doubleValue() : null,
                        ((Number) r[4]).doubleValue()
                ))
                .toList();
    }

    public List<UserSpendRanking> findUserRankingBySpend(
            Session session,
            long minUserId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            WITH user_totals AS (
                SELECT
                    b.user_id,
                    SUM(b.amount) AS total_spend,
                    COUNT(DISTINCT b.auction_id) AS auctions_participated
                FROM bid b
                WHERE b.user_id >= :minUserId
                GROUP BY b.user_id
            )
            SELECT
                ut.user_id,
                ut.total_spend,
                ut.auctions_participated,
                DENSE_RANK() OVER (ORDER BY ut.total_spend DESC) AS spend_rank
            FROM user_totals ut
            ORDER BY spend_rank
        """)
                .setParameter("minUserId", minUserId)
                .getResultList();

        return rows.stream()
                .map(r -> new UserSpendRanking(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).doubleValue(),
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).intValue()
                ))
                .toList();
    }

    public List<BidIntensitySlot> findBidIntensityByAuction(
            Session session,
            Long auctionId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            SELECT
                DATE_TRUNC('minute', b.placed_at) AS minute,
                COUNT(b.bid_id) AS bids_in_slot,
                MAX(b.amount) AS max_bid_in_slot,
                SUM(COUNT(b.bid_id)) OVER (ORDER BY DATE_TRUNC('minute', b.placed_at)) AS cumulative_bids
            FROM bid b
            WHERE b.auction_id = :auctionId
            GROUP BY DATE_TRUNC('minute', b.placed_at)
            ORDER BY minute
        """)
                .setParameter("auctionId", auctionId)
                .getResultList();

        return rows.stream()
                .map(r -> new BidIntensitySlot(
                        HelperFunctions.toLocalDateTime(r[0]),
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).doubleValue(),
                        ((Number) r[3]).longValue()
                ))
                .toList();
    }
}
