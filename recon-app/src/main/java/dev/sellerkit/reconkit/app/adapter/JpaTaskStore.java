package dev.sellerkit.reconkit.app.adapter;

import dev.sellerkit.reconkit.app.repo.ReprocessTaskRepository;
import dev.sellerkit.reconkit.domain.enums.TaskState;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import dev.sellerkit.reconkit.reprocess.TaskStore;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database backed task store.
 *
 * <p>Every method runs in its own transaction. That is the whole point: a state transition
 * has to be visible to other workers the instant it happens, and a transition that only
 * commits when the surrounding job finishes protects nothing at all, because the window it
 * is meant to close is exactly the time the job is running.
 */
@Component
public class JpaTaskStore implements TaskStore {

    private final ReprocessTaskRepository tasks;

    public JpaTaskStore(ReprocessTaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transition(Long taskId, TaskState from, TaskState to) {
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException("illegal transition " + from + " to " + to);
        }
        return tasks.transition(taskId, from, to, java.time.Instant.now()) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(Long taskId, String lastError) {
        tasks.recordAttempt(taskId, truncate(lastError));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ReprocessTask> tasksOf(Long jobId) {
        return tasks.findByJobIdOrderByStepNoAsc(jobId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReprocessTask reload(Long taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("task " + taskId + " disappeared"));
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 990 ? value : value.substring(0, 990) + "...";
    }
}
