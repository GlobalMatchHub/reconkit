package dev.sellerkit.reconkit.app.adapter;

import dev.sellerkit.reconkit.app.audit.AuditService;
import dev.sellerkit.reconkit.app.repo.ReprocessJobRepository;
import dev.sellerkit.reconkit.domain.enums.JobState;
import dev.sellerkit.reconkit.domain.model.ReprocessJob;
import dev.sellerkit.reconkit.reprocess.JobOutcome;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job level state changes, in their own transactions.
 *
 * <p>A separate bean rather than methods on the service, because a transactional method
 * called from another method of the same class goes straight to the object and never
 * through the proxy: the annotation is silently ignored and the "separate transaction" is
 * the caller's. That is invisible in a code review and fatal here, where the whole design
 * depends on a state change being committed while the job is still running.
 */
@Component
public class JobStateStore {

    private final ReprocessJobRepository jobs;
    private final AuditService audit;

    public JobStateStore(ReprocessJobRepository jobs, AuditService audit) {
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReprocessJob markRunning(Long jobId) {
        ReprocessJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("unknown job " + jobId));
        if (job.getState() == JobState.PENDING) {
            job.setState(JobState.RUNNING);
            job.setStartedAt(Instant.now());
            jobs.saveAndFlush(job);
        }
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long jobId, JobOutcome outcome) {
        ReprocessJob job = jobs.findById(jobId).orElseThrow();
        job.setState(outcome.state());
        job.setTaskSucceeded(outcome.succeeded());
        job.setTaskFailed(outcome.failed());
        job.setTaskCompensated(outcome.compensated());
        job.setFailureReason(outcome.reason());
        if (outcome.state() != JobState.RUNNING) {
            job.setFinishedAt(Instant.now());
        }
        jobs.saveAndFlush(job);
        audit.record("REPROCESS_FINISH", "ReprocessJob", jobId, Map.of(
                "state", outcome.state().name(),
                "succeeded", outcome.succeeded(),
                "compensated", outcome.compensated(),
                "escalated", outcome.escalated()));
    }
}
