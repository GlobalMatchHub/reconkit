package dev.sellerkit.reconkit.reprocess;

import dev.sellerkit.reconkit.domain.enums.TaskState;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stands in for the database. {@link #transition} is synchronized so that it behaves the
 * way the real conditional update does: exactly one caller wins.
 */
final class InMemoryTaskStore implements TaskStore {

    private final Map<Long, ReprocessTask> tasks = new LinkedHashMap<>();
    private final List<String> transitionLog = new ArrayList<>();

    void put(ReprocessTask task) {
        tasks.put(task.getId(), task);
    }

    List<String> transitionLog() {
        return transitionLog;
    }

    @Override
    public synchronized boolean transition(Long taskId, TaskState from, TaskState to) {
        ReprocessTask task = tasks.get(taskId);
        if (task == null || task.getState() != from) {
            return false;
        }
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException("illegal transition " + from + " to " + to);
        }
        task.setState(to);
        transitionLog.add(taskId + ":" + from + ">" + to);
        return true;
    }

    @Override
    public synchronized void recordAttempt(Long taskId, String lastError) {
        ReprocessTask task = tasks.get(taskId);
        task.setAttempt(task.getAttempt() + 1);
        task.setLastError(lastError);
    }

    @Override
    public synchronized List<ReprocessTask> tasksOf(Long jobId) {
        List<ReprocessTask> found = new ArrayList<>();
        tasks.values().stream().filter(t -> t.getJobId().equals(jobId)).forEach(found::add);
        return found;
    }

    @Override
    public synchronized ReprocessTask reload(Long taskId) {
        return tasks.get(taskId);
    }
}
