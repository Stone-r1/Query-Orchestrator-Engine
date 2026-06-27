package org.example.queries.queryRunner;


import org.example.models.dto.AuctionBidderOverlap;
import org.example.models.dto.AuctionLeaderboardEntry;
import org.example.models.dto.AuctionPriceVelocity;
import org.example.models.dto.AuctionStats;
import org.example.models.dto.OutbidAuctionSummary;
import org.example.models.entities.Auction;
import org.hibernate.Session;

import java.util.List;


@SuppressWarnings("unchecked")
public class AuctionQueries {
    public void insertAuction(
            Session session,
            Auction auction
    ) {
        session.persist(auction);
    }

    public void deleteAllAuctions(
            Session session
    ) {
        session.createMutationQuery("DELETE FROM Auction").executeUpdate();
    }

    public void deleteAuctionById(
            Session session,
            long auctionId
    ) {
        session.createMutationQuery("DELETE FROM Auction a WHERE a.auctionId = :auctionId")
                .setParameter("auctionId", auctionId)
                .executeUpdate();
    }

    public AuctionStats getAuctionStats(
            Session session,
            Long auctionId
    ) {

        Object[] r = (Object[]) session.createNativeQuery("""
            SELECT a.auction_id,
                   a.item_name,
                   COUNT(b.bid_id),
                   MAX(b.amount)
            FROM auction a
            LEFT JOIN bid b ON b.auction_id = a.auction_id
            WHERE a.auction_id = :id
            GROUP BY a.auction_id, a.item_name
        """)
                .setParameter("id", auctionId)
                .getSingleResult();

        return new AuctionStats(
                ((Number) r[0]).longValue(),
                (String) r[1],
                ((Number) r[2]).longValue(),
                r[3] != null ? ((Number) r[3]).doubleValue() : null
        );
    }

    public List<Auction> findByMinMaxBid(
            Session session,
            Double minBid
    ) {
        return session.createQuery("""
            FROM Auction a
            WHERE a.maxBid >= :minBid
        """, Auction.class)
                .setParameter("minBid", minBid)
                .getResultList();
    }

    public List<AuctionLeaderboardEntry> findAuctionLeaderboard(
            Session session,
            Long auctionId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            SELECT
                b.auction_id,
                b.user_id,
                MAX(b.amount) AS top_bid,
                DENSE_RANK() OVER (ORDER BY MAX(b.amount) DESC) AS bid_rank,
                COUNT(b.bid_id) AS bid_count,
                MAX(b.amount) - a.starting_price AS spread
            FROM bid b
            JOIN auction a ON a.auction_id = b.auction_id
            WHERE b.auction_id = :auctionId
            GROUP BY b.auction_id, b.user_id, a.starting_price
            ORDER BY bid_rank
        """)
                .setParameter("auctionId", auctionId)
                .getResultList();

        return rows.stream()
                .map(r -> new AuctionLeaderboardEntry(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).doubleValue(),
                        ((Number) r[3]).intValue(),
                        ((Number) r[4]).longValue(),
                        ((Number) r[5]).doubleValue()
                ))
                .toList();
    }

    public List<OutbidAuctionSummary> findAuctionsWithOutbidUsers(
            Session session,
            long minBidCount
    ) {
        List<Object[]> rows = session.createNativeQuery("""
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
            HAVING COUNT(b.bid_id) >= :minBidCount
            ORDER BY outbid_user_count DESC
        """)
                .setParameter("minBidCount", minBidCount)
                .getResultList();

        return rows.stream()
                .map(r -> new OutbidAuctionSummary(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).longValue(),
                        ((Number) r[4]).doubleValue(),
                        ((Number) r[5]).doubleValue()
                ))
                .toList();
    }

    public List<AuctionPriceVelocity> findAuctionPriceVelocity(
            Session session,
            Long auctionId
    ) {
        List<Object[]> rows = session.createNativeQuery("""
            WITH quartiled AS (
                SELECT
                    b.auction_id,
                    b.amount,
                    NTILE(4) OVER (ORDER BY b.placed_at) AS quartile
                FROM bid b
                WHERE b.auction_id = :auctionId
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
        """)
                .setParameter("auctionId", auctionId)
                .getResultList();

        return rows.stream()
                .map(r -> new AuctionPriceVelocity(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).intValue(),
                        ((Number) r[2]).longValue(),
                        ((Number) r[3]).doubleValue(),
                        ((Number) r[4]).doubleValue()
                ))
                .toList();
    }

    public List<AuctionBidderOverlap> findCrossAuctionBidderOverlap(Session session) {
        List<Object[]> rows = session.createNativeQuery("""
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
        """)
                .getResultList();

        return rows.stream()
                .map(r -> new AuctionBidderOverlap(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).longValue()
                ))
                .toList();
    }
}
