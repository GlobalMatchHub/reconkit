package dev.sellerkit.reconkit.app.service;

import dev.sellerkit.reconkit.app.adapter.JobStateStore;
import dev.sellerkit.reconkit.app.audit.AuditService;
import dev.sellerkit.reconkit.app.repo.ReprocessJobRepository;
import dev.sellerkit.reconkit.app.repo.ReprocessTaskRepository;
import dev.sellerkit.reconkit.domain.enums.JobState;
import dev.sellerkit.reconkit.domain.model.ReprocessJob;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import dev.sellerkit.reconkit.reprocess.JobOutcome;
import dev.sellerkit.reconkit.reprocess.SagaRunner;
import dev.sellerkit.reconkit.reprocess.TaskHandlerRegistry;
import dev.sellerkit.reconkit.reprocess.TaskStore;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts reprocess requests and runs them.
 *
 * <p>The submission and the execution are separate transactions on purpose. The job and
 * its tasks are committed first, so that a request which arrives twice finds the first
 * job already recorded, whatever state it is in. Creating the job inside the same
 * transaction that runs it means a duplicate arriving while the first is still working
 * sees nothing and starts a second one, which is the case the idempotency key exists for
 * and the one it would then fail to cover.
 */
@Service
public class ReprocessService {

    private static final Logger log = LoggerFactory.getLogger(ReprocessService.class);

    private final ReprocessJobRepository jobs;
    private final ReprocessTaskRepository tasks;
    private final TaskStore taskStore;
    private final TaskHandlerRegistry registry;
    private final JobStateStore jobState;
    private final AuditService audit;

    public ReprocessService(ReprocessJobRepository jobs,
                            ReprocessTaskRepository tasks,
                            TaskStore taskStore,
                            TaskHandlerRegistry registry,
                            JobStateStore jobState,
                            AuditService audit) {
        this.jobs = jobs;
        this.tasks = tasks;
        this.taskStore = taskStore;
        this.registry = registry;
        this.jobState = jobState;
        this.audit = audit;
    }

    public record Step(String action, String payloadJson, String compensationJson) {
    }

    public record Submission(ReprocessJob job, boolean alreadyExisted) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Submission submit(String idempotencyKey, String jobType, String targetRef, List<Step> steps) {
        var existing = jobs.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("reprocess request {} was already accepted as job {}",
                    idempotencyKey, existing.get().getId());
            return new Submission(existing.get(), true);
        }

        ReprocessJob job = new ReprocessJob(idempotencyKey, jobType, targetRef, AuditService.currentActor());
        job.setTaskTotal(steps.size());
        try {
            jobs.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            // Two identical requests raced. The one that lost reports the winner's job
            // rather than failing: from the caller's point of view the request was accepted.
            return new Submission(jobs.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex), true);
        }

        for (int index = 0; index < steps.size(); index++) {
            Step step = steps.get(index);
            tasks.save(new ReprocessTask(job.getId(), idempotencyKey + ":" + (index + 1), index + 1,
                    step.action(), step.payloadJson(), step.compensationJson()));
        }
        audit.record("REPROCESS_SUBMIT", "ReprocessJob", job.getId(), Map.of(
                "idempotencyKey", idempotencyKey,
                "type", jobType,
                "target", targetRef,
                "steps", steps.size()));
        return new Submission(job, false);
    }

    public JobOutcome execute(Long jobId) {
        ReprocessJob job = jobState.markRunning(jobId);
        if (job.getState() != JobState.PENDING && job.getState() != JobState.RUNNING) {
            log.info("job {} is already {}", jobId, job.getState());
            return new JobOutcome(job.getState(), job.getTaskSucceeded(), job.getTaskFailed(),
                    job.getTaskCompensated(), 0, job.getFailureReason());
        }
        JobOutcome outcome = new SagaRunner(taskStore, registry).run(job);
        jobState.finish(jobId, outcome);
        return outcome;
    }

}
