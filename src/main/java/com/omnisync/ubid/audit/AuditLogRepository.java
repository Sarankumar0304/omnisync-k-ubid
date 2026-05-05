package com.omnisync.ubid.audit;

import com.omnisync.ubid.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    List<AuditLog> findByUbidOrderByTimestampDesc(String ubid);

    List<AuditLog> findByCorrelationIdOrderByTimestamp(String correlationId);

    List<AuditLog> findByEventTypeOrderByTimestampDesc(AuditLog.AuditEventType eventType);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.outcome = 'CONFLICT_RESOLVED'")
    long countConflictsResolved();

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.eventType = 'PROPAGATION_SUCCESS'")
    long countSuccessful();

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since")
    long countSince(@Param("since") Instant since);
}
