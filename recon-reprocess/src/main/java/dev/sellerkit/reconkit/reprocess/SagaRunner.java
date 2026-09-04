package dev.sellerkit.reconkit.reprocess;

import dev.sellerkit.reconkit.domain.enums.JobState;
import dev.sellerkit.reconkit.domain.enums.TaskState;
import dev.sellerkit.reconkit.domain.model.ReprocessJob;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a job's tasks in order and rolls back what succeeded when one of them does not.
 *
 * <p>Three rules, each of which exists because of the way this goes wrong in practice.
 *
 * <p>A task is claimed with a conditional transition. If the claim fails, the task belongs
 * to somebody else and this runner leaves it alone rather than executing it anyway. A
 * duplicate scheduler tick, a retried HTTP call and a manual re-run all arrive as a
 * second claim, and all three have to lose.
 *
 * <p>Rollback runs in reverse, over the tasks that actually succeeded, using the
 * compensation recorded when each task was created. Undoing in forward order, or working
 * out the compensation at rollback time, both fail on the case that matters: step three
 * failed because the world moved, and the world has been moved.
 *
 * <p>A step that cannot be undone stops the machine. It is marked escalated and a person
 * is told, because the alternative, retrying an irreversible action until it appears to
 * work, is how one failed settlement becomes several paid ones.
 */
public final class SagaRunner {

    private static final Logger log = LoggerFactory.getLogger(SagaRunner.class);

    private final TaskStore store;
    private final TaskHandlerRegistry registry;

    public SagaRunner(TaskStore store, TaskHandlerRegistry registry) {
        this.store = store;
        this.registry = registry;
    }

    public JobOutcome run(ReprocessJob job) {
        List<ReprocessTask> tasks = store.tasksOf(job.getId());
        tasks.sort(Comparator.comparingInt(ReprocessTask::getStepNo));

        Deque<ReprocessTask> completed = new ArrayDeque<>();
        int succeeded = 0;
        String failureReason = null;
        ReprocessTask failedTask = null;

        for (ReprocessTask task : tasks) {
            if (task.getState().isTerminal()) {
                if (task.getState() == TaskState.SUCCEEDED) {
                    completed.push(task);
                    succeeded++;
                }
                continue;
            }
            Outcome outcome = runOne(task);
            if (outcome == Outcome.SUCCEEDED) {
                completed.push(store.reload(task.getId()));
                succeeded++;
                continue;
            }
            if (outcome == Outcome.NOT_MINE) {
                log.info("task {} is being run elsewhere, leaving job {} to that runner",
                        task.getId(), job.getId());
                return new JobOutcome(JobState.RUNNING, succeeded, 0, 0, 0,
                        "another runner holds step " + task.getStepNo());
            }
            failedTask = task;
            failureReason = store.reload(task.getId()).getLastError();
            break;
        }

        if (failedTask == null) {
            return new JobOutcome(JobState.SUCCEEDED, succeeded, 0, 0, 0, null);
        }

        Rollback rollback = compensate(completed);
        JobState state = rollback.escalated > 0 ? JobState.ESCALATED : JobState.COMPENSATED;
        String reason = "step %d (%s) failed: %s".formatted(
                failedTask.getStepNo(), failedTask.getAction(), failureReason);
        if (rollback.escalated > 0) {
            reason += "; %d completed step(s) could not be undone".formatted(rollback.escalated);
        }
        return new JobOutcome(state, succeeded, 1, rollback.compensated, rollback.escalated, reason);
    }

    private Outcome runOne(ReprocessTask task) {
        TaskState from = task.getState();
        if (from != TaskState.PENDING && from != TaskState.FAILED) {
            return Outcome.NOT_MINE;
        }
        if (!store.transition(task.getId(), from, TaskState.CLAIMED)) {
            return Outcome.NOT_MINE;
        }
        if (!store.transition(task.getId(), TaskState.CLAIMED, TaskState.IN_FLIGHT)) {
            return Outcome.NOT_MINE;
        }
        try {
            registry.require(task.getAction()).execute(store.reload(task.getId()));
            store.transition(task.getId(), TaskState.IN_FLIGHT, TaskState.SUCCEEDED);
            return Outcome.SUCCEEDED;
        } catch (RuntimeException ex) {
            log.warn("task {} ({}) failed: {}", task.getId(), task.getAction(), ex.toString());
            store.recordAttempt(task.getId(), ex.toString());
            store.transition(task.getId(), TaskState.IN_FLIGHT, TaskState.FAILED);
            ReprocessTask reloaded = store.reload(task.getId());
            if (reloaded.hasAttemptsLeft()) {
                return runOne(reloaded);
            }
            return Outcome.FAILED;
        }
    }

    private Rollback compensate(Deque<ReprocessTask> completed) {
        int compensated = 0;
        int escalated = 0;
        while (!completed.isEmpty()) {
            ReprocessTask task = completed.pop();
            if (!task.isReversible()) {
                store.transition(task.getId(), TaskState.SUCCEEDED, TaskState.COMPENSATING);
                store.transition(task.getId(), TaskState.COMPENSATING, TaskState.ESCALATED);
                store.recordAttempt(task.getId(), "step is not reversible, escalated for manual handling");
                escalated++;
                continue;
            }
            if (!store.transition(task.getId(), TaskState.SUCCEEDED, TaskState.COMPENSATING)) {
                continue;
            }
            try {
                registry.require(task.getAction()).compensate(store.reload(task.getId()));
                store.transition(task.getId(), TaskState.COMPENSATING, TaskState.COMPENSATED);
                compensated++;
            } catch (RuntimeException ex) {
                log.error("compensation for task {} failed: {}", task.getId(), ex.toString());
                store.recordAttempt(task.getId(), "compensation failed: " + ex);
                store.transition(task.getId(), TaskState.COMPENSATING, TaskState.ESCALATED);
                escalated++;
            }
        }
        return new Rollback(compensated, escalated);
    }

    private enum Outcome {
        SUCCEEDED,
        FAILED,
        /** Another runner holds the task. Not an error, and not something to retry. */
        NOT_MINE
    }

    private record Rollback(int compensated, int escalated) {
    }
}
