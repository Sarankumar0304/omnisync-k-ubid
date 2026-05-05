package com.omnisync.ubid.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Stores the last known snapshot of a department record for a given UBID.
 * Used by the Sidecar Poller to detect changes in "silent" legacy systems
 * that don't natively emit events or webhooks.
 */
@Entity
@Table(name = "department_snapshots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ubid", "department_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "ubid", nullable = false, length = 64)
    private String ubid;

    @Column(name = "department_id", nullable = false, length = 64)
    private String departmentId;

    /** SHA-256 hash of the last polled record — diff detection */
    @Column(name = "content_hash", length = 128)
    private String contentHash;

    /** Full JSON of the last polled record */
    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "change_detected_at")
    private Instant changeDetectedAt;
}
