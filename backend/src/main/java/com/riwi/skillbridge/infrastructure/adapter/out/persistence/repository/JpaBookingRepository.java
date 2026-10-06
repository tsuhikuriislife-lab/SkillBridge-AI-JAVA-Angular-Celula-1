package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, UUID> {
    @Query(value = """
	    SELECT b.id AS "id",
		   b.offering_id AS "offeringId",
		   o.title AS "offeringTitle",
		   o.price AS "price",
		   b.scheduled_at AS "scheduledAt",
		   b.status AS "status"
	    FROM bookings b
	    JOIN offerings o ON o.id = b.offering_id
	    WHERE b.customer_id = :customerId
	      AND (:activity = 'ALL'
		   OR (:activity = 'ACTIVE' AND b.status IN ('CREATED', 'CONFIRMED'))
		   OR (:activity = 'INACTIVE' AND b.status IN ('CANCELLED', 'COMPLETED')))
	    ORDER BY CASE WHEN :sort = 'TITLE_ASC' THEN LOWER(o.title) END ASC,
		     CASE WHEN :sort = 'TITLE_DESC' THEN LOWER(o.title) END DESC,
		     CASE WHEN :sort = 'DATE_ASC' THEN b.scheduled_at END ASC,
		     CASE WHEN :sort = 'DATE_DESC' THEN b.scheduled_at END DESC,
		     CASE WHEN :sort = 'PRICE_ASC' THEN o.price END ASC,
		     CASE WHEN :sort = 'PRICE_DESC' THEN o.price END DESC,
		     b.id DESC
	    """,
	    countQuery = """
		    SELECT COUNT(*)
		    FROM bookings b
		    WHERE b.customer_id = :customerId
		      AND (:activity = 'ALL'
			   OR (:activity = 'ACTIVE' AND b.status IN ('CREATED', 'CONFIRMED'))
			   OR (:activity = 'INACTIVE' AND b.status IN ('CANCELLED', 'COMPLETED')))
		    """,
	    nativeQuery = true)
    Page<BookingListingProjection> findListingByCustomerId(
	    @Param("customerId") UUID customerId,
			@Param("activity") String activity,
			@Param("sort") String sort,
	    Pageable pageable
    );
}
