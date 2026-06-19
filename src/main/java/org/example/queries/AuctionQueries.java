package org.example.queries;


import org.example.models.dto.AuctionStats;
import org.example.models.entities.Auction;
import org.hibernate.Session;

import java.time.LocalDateTime;
import java.util.List;

public class AuctionQueries {
    public void insertAuction(
            Session session,
            Auction auction
    ) {
        session.persist(auction);
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

    public List<Auction> findActiveAuctions(
            Session session,
            LocalDateTime now
    ) {
        return session.createQuery("""
            FROM Auction a
            WHERE a.startDate <= :now
                AND a.endDate > :now
        """, Auction.class)
                .setParameter("now", now)
                .getResultList();
    }

    public List<Auction> findWithoutBids(
            Session session
    ) {
        return session.createQuery("""
            SELECT a
            FROM Auction a
            WHERE NOT EXISTS (
                SELECT 1 FROM Bid b WHERE b.auctionId = a.auctionId
            )
        """, Auction.class)
                .getResultList();
    }

    public List<Auction> findEndingBetween(
            Session session,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return session.createQuery("""
            FROM Auction a
            WHERE a.endDate BETWEEN :from AND :to
            ORDER BY a.endDate ASC
        """, Auction.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }
}
