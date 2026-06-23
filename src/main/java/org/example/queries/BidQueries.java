package org.example.queries;


import org.example.models.dto.BidRanks;
import org.example.models.dto.TopBidder;
import org.example.models.entities.Bid;
import org.hibernate.Session;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;


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
                        toLocalDateTime(r[2]),
                        ((Number) r[3]).intValue()
                ))
                .toList();
    }

    /*
     * The JDBC driver may hand back a TIMESTAMP column either as java.sql.Timestamp
     * (legacy) or java.time.LocalDateTime (PostgreSQL driver for 'timestamp without
     * time zone'). Normalise both so the mapping never blows up with a ClassCastException.
     */
    private static LocalDateTime toLocalDateTime(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDateTime localDateTime -> localDateTime;
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            default -> throw new IllegalStateException(
                    "Unexpected timestamp type: " + value.getClass().getName());
        };
    }
}
