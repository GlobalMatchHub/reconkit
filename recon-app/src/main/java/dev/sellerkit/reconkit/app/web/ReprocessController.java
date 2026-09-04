package dev.sellerkit.reconkit.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sellerkit.reconkit.app.repo.ReprocessJobRepository;
import dev.sellerkit.reconkit.app.repo.ReprocessTaskRepository;
import dev.sellerkit.reconkit.app.service.ReprocessService;
import dev.sellerkit.reconkit.domain.model.ReprocessJob;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reprocess")
public class ReprocessController {

    private final ReprocessService service;
    private final ReprocessJobRepository jobs;
    private final ReprocessTaskRepository tasks;
    private final ObjectMapper objectMapper;

    public ReprocessController(ReprocessService service, ReprocessJobRepository jobs,
                               ReprocessTaskRepository tasks, ObjectMapper objectMapper) {
        this.service = service;
        this.jobs = jobs;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
    }

    public record StepRequest(String action, Map<String, Object> payload, Map<String, Object> compensation) {
    }

    public record JobRequest(String jobType, String targetRef, List<StepRequest> steps) {
    }

    public record TaskView(Long id, int stepNo, String action, String state, int attempt, int maxAttempts,
                           boolean reversible, String lastError, Instant claimedAt) {
    }

    public record JobView(Long id, String idempotencyKey, String jobType, String targetRef, String state,
                          String requestedBy, int taskTotal, int taskSucceeded, int taskFailed,
                          int taskCompensated, Instant startedAt, Instant finishedAt,
                          String failureReason, List<TaskView> tasks) {
    }

    /**
     * Submits and runs a reprocess job.
     *
     * <p>The idempotency key is supplied by the caller in a header. A retried request,
     * whether from a browser that timed out or an operator pressing the button twice,
     * carries the same key and is answered with the original job rather than starting a
     * second one. The response says which happened, and a repeat is 200 rather than 201.
     */
    @PostMapping
    public ResponseEntity<JobView> submit(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody JobRequest request) {
        List<ReprocessService.Step> steps = request.steps().stream()
                .map(step -> new ReprocessService.Step(step.action(),
                        toJson(step.payload()), toJson(step.compensation())))
                .toList();

        ReprocessService.Submission submission =
                service.submit(idempotencyKey, request.jobType(), request.targetRef(), steps);
        if (submission.alreadyExisted()) {
            return ResponseEntity.ok(toView(submission.job()));
        }
        service.execute(submission.job().getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toView(jobs.findById(submission.job().getId()).orElseThrow()));
    }

    @GetMapping
    public List<JobView> list() {
        return jobs.findTop100ByOrderByIdDesc().stream().map(this::toView).toList();
    }

    @GetMapping("/{id}")
    public JobView one(@PathVariable Long id) {
        return toView(jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown job " + id)));
    }

    private JobView toView(ReprocessJob job) {
        List<TaskView> taskViews = tasks.findByJobIdOrderByStepNoAsc(job.getId()).stream()
                .map(this::toView).toList();
        return new JobView(job.getId(), job.getIdempotencyKey(), job.getJobType(), job.getTargetRef(),
                job.getState().name(), job.getRequestedBy(), job.getTaskTotal(), job.getTaskSucceeded(),
                job.getTaskFailed(), job.getTaskCompensated(), job.getStartedAt(), job.getFinishedAt(),
                job.getFailureReason(), taskViews);
    }

    private TaskView toView(ReprocessTask task) {
        return new TaskView(task.getId(), task.getStepNo(), task.getAction(), task.getState().name(),
                task.getAttempt(), task.getMaxAttempts(), task.isReversible(), task.getLastError(),
                task.getClaimedAt());
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payload is not serialisable", ex);
        }
    }
}
