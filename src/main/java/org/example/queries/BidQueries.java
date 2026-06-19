package org.example.queries;


import org.example.models.dto.BidRanks;
import org.example.models.dto.TopBidder;
import org.example.models.entities.Bid;
import org.hibernate.Session;

import java.sql.Timestamp;
import java.util.List;


public class BidQueries {

    public void insertBid(
            Session session,
            Bid bid
    ) {
        session.persist(bid);
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

        session.close();

        return rows.stream()
                .map(r -> new BidRanks(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).doubleValue(),
                        ((Timestamp) r[2]).toLocalDateTime(),
                        ((Number) r[3]).intValue()
                ))
                .toList();
    }
}
