package dev.sellerkit.reconkit.reprocess;

import dev.sellerkit.reconkit.domain.enums.TaskState;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import java.util.List;

/**
 * The only way the runner is allowed to move a task.
 *
 * <p>{@link #transition} is a conditional update, not a read followed by a write. It
 * returns true only when exactly one row changed, which means the caller was the one
 * that moved it. Two workers that both believe a task is PENDING will both call this, and
 * exactly one gets true.
 *
 * <p>This is the part an idempotency key does not cover. The key stops a second request
 * from creating a second job. It says nothing about a second worker picking up a task
 * that a first worker is already executing, and that is the case that pays a merchant
 * twice. The guard has to be on the transition, in the database, in the same statement
 * that does the move.
 */
public interface TaskStore {

    /**
     * Moves a task, but only if it is still in {@code from}.
     *
     * @return true when this caller performed the move
     */
    boolean transition(Long taskId, TaskState from, TaskState to);

    void recordAttempt(Long taskId, String lastError);

    List<ReprocessTask> tasksOf(Long jobId);

    ReprocessTask reload(Long taskId);
}
