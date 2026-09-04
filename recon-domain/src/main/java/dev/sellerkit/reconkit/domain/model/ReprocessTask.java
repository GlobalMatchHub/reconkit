package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.TaskState;
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
 * One step of a job, and the smallest thing that can be retried on its own.
 *
 * <p>Each task carries the description of how to undo itself. Recording the
 * compensation up front, rather than deriving it at rollback time, is what makes the
 * rollback survive the situation it exists for: by the time it runs, the state it
 * would have derived the answer from has already moved.
 */
@Entity
@Table(name = "reprocess_task",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reprocess_task_idem",
                columnNames = {"tenant_id", "idempotency_key"}))
public class ReprocessTask extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** How to undo this step if a later step fails. Null means the step is not reversible. */
    @Column(name = "compensation_json", columnDefinition = "TEXT")
    private String compensationJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private TaskState state = TaskState.PENDING;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ReprocessTask() {
    }

    public ReprocessTask(Long jobId, String idempotencyKey, int stepNo, String action,
                         String payloadJson, String compensationJson) {
        this.jobId = jobId;
        this.idempotencyKey = idempotencyKey;
        this.stepNo = stepNo;
        this.action = action;
        this.payloadJson = payloadJson;
        this.compensationJson = compensationJson;
    }

    public boolean isReversible() {
        return compensationJson != null && !compensationJson.isBlank();
    }

    public boolean hasAttemptsLeft() {
        return attempt < maxAttempts;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getStepNo() {
        return stepNo;
    }

    public String getAction() {
        return action;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getCompensationJson() {
        return compensationJson;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(TaskState v) {
        this.state = v;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int v) {
        this.attempt = v;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String v) {
        this.lastError = v;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant v) {
        this.claimedAt = v;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant v) {
        this.finishedAt = v;
    }
}
