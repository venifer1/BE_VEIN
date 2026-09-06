package com.vein.tvl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TvlSnapshotRepository extends JpaRepository<TvlSnapshot, Long> {

    Optional<TvlSnapshot> findTopByEntityTypeOrderByCollectedAtDesc(String entityType);

    Optional<TvlSnapshot> findTopByOrderByCollectedAtDesc();

    /** All rows from the latest batch for a given entity_type. */
    @Query("""
            select t from TvlSnapshot t
            where t.entityType = :type and t.collectedAt = (
                select max(t2.collectedAt) from TvlSnapshot t2 where t2.entityType = :type)
            """)
    List<TvlSnapshot> findLatestBatch(@Param("type") String entityType);

    /** External id (slug) of a protocol in the latest batch, used for history lookup. */
    @Query("""
            select t.externalId from TvlSnapshot t
            where t.entityType = 'PROTOCOL' and t.id = :id
            """)
    Optional<String> findProtocolSlugById(@Param("id") Long id);

    void deleteByCollectedAtBefore(Instant cutoff);
}
