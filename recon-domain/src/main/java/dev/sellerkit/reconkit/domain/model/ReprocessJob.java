package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.JobState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A unit of work an operator asked for: re-run a date, re-post a set of entries,
 * regenerate a settlement.
 *
 * <p>The idempotency key is unique per tenant. A second request carrying the same key
 * does not start a second job, it returns the first one, whatever state it is in. That
 * distinction matters more than it looks: the dangerous case is not the duplicate that
 * arrives after the job finished, it is the one that arrives while the job is still running.
 */
@Entity
@Table(name = "reprocess_job",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reprocess_job_idem",
                columnNames = {"tenant_id", "idempotency_key"}))
public class ReprocessJob extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "job_type", nullable = false, length = 40)
    private String jobType;

    @Column(name = "target_ref", nullable = false, length = 120)
    private String targetRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private JobState state = JobState.PENDING;

    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;

    @Column(name = "task_total", nullable = false)
    private int taskTotal;

    @Column(name = "task_succeeded", nullable = false)
    private int taskSucceeded;

    @Column(name = "task_failed", nullable = false)
    private int taskFailed;

    @Column(name = "task_compensated", nullable = false)
    private int taskCompensated;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected ReprocessJob() {
    }

    public ReprocessJob(String idempotencyKey, String jobType, String targetRef, String requestedBy) {
        this.idempotencyKey = idempotencyKey;
        this.jobType = jobType;
        this.targetRef = targetRef;
        this.requestedBy = requestedBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getJobType() {
        return jobType;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState v) {
        this.state = v;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public int getTaskTotal() {
        return taskTotal;
    }

    public void setTaskTotal(int v) {
        this.taskTotal = v;
    }

    public int getTaskSucceeded() {
        return taskSucceeded;
    }

    public void setTaskSucceeded(int v) {
        this.taskSucceeded = v;
    }

    public int getTaskFailed() {
        return taskFailed;
    }

    public void setTaskFailed(int v) {
        this.taskFailed = v;
    }

    public int getTaskCompensated() {
        return taskCompensated;
    }

    public void setTaskCompensated(int v) {
        this.taskCompensated = v;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant v) {
        this.startedAt = v;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant v) {
        this.finishedAt = v;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String v) {
        this.failureReason = v;
    }
}
