package dev.sellerkit.reconkit.reprocess;

import dev.sellerkit.reconkit.domain.model.ReprocessTask;

/**
 * What a task actually does, and how to undo it.
 *
 * <p>{@code compensate} is given the task, which carries the compensation description
 * recorded when the task was created. It is not asked to work out what to undo from the
 * current state, because by the time it runs the current state is precisely what is wrong.
 */
public interface TaskHandler {

    String action();

    void execute(ReprocessTask task);

    /**
     * Undoes a completed execution. A handler that cannot undo its work throws
     * {@link IrreversibleTaskException}, which stops the rollback and escalates instead
     * of quietly leaving half a job applied.
     */
    void compensate(ReprocessTask task);
}
