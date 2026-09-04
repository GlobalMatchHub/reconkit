package dev.sellerkit.reconkit.reprocess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sellerkit.reconkit.domain.enums.JobState;
import dev.sellerkit.reconkit.domain.enums.TaskState;
import dev.sellerkit.reconkit.domain.model.ReprocessJob;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SagaRunnerTest {

    private final InMemoryTaskStore store = new InMemoryTaskStore();
    private final List<String> effects = new ArrayList<>();

    private ReprocessJob job() {
        ReprocessJob job = new ReprocessJob("idem-1", "RERUN_RECON", "2026-08-17", "operator@example.com");
        job.setId(100L);
        return job;
    }

    private ReprocessTask task(long id, int step, String action, boolean reversible) {
        ReprocessTask task = new ReprocessTask(100L, "idem-1:" + step, step, action,
                "{\"step\":" + step + "}", reversible ? "{\"undo\":" + step + "}" : null);
        task.setId(id);
        store.put(task);
        return task;
    }

    private TaskHandler handler(String action, Runnable body) {
        return new TaskHandler() {
            @Override
            public String action() {
                return action;
            }

            @Override
            public void execute(ReprocessTask task) {
                effects.add("do:" + task.getStepNo());
                body.run();
            }

            @Override
            public void compensate(ReprocessTask task) {
                if (!task.isReversible()) {
                    throw new IrreversibleTaskException("step " + task.getStepNo() + " cannot be undone");
                }
                effects.add("undo:" + task.getStepNo());
            }
        };
    }

    @Test
    @DisplayName("every step succeeding leaves the job succeeded and nothing rolled back")
    void allStepsSucceed() {
        task(1L, 1, "POST", true);
        task(2L, 2, "POST", true);
        TaskHandlerRegistry registry = new TaskHandlerRegistry().register(handler("POST", () -> {
        }));

        JobOutcome outcome = new SagaRunner(store, registry).run(job());

        assertThat(outcome.state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(outcome.succeeded()).isEqualTo(2);
        assertThat(effects).containsExactly("do:1", "do:2");
    }

    @Test
    @DisplayName("a failing step rolls the completed steps back in reverse order")
    void failureRollsBackInReverse() {
        task(1L, 1, "POST", true);
        task(2L, 2, "POST", true);
        task(3L, 3, "BOOM", true);
        TaskHandlerRegistry registry = new TaskHandlerRegistry()
                .register(handler("POST", () -> {
                }))
                .register(handler("BOOM", () -> {
                    throw new IllegalStateException("gateway rejected the reversal");
                }));

        JobOutcome outcome = new SagaRunner(store, registry).run(job());

        assertThat(outcome.state()).isEqualTo(JobState.COMPENSATED);
        assertThat(outcome.compensated()).isEqualTo(2);
        assertThat(effects).containsExactly("do:1", "do:2", "do:3", "do:3", "do:3", "undo:2", "undo:1");
        assertThat(store.reload(3L).getState()).isEqualTo(TaskState.FAILED);
        assertThat(store.reload(3L).getAttempt()).isEqualTo(3);
    }

    @Test
    @DisplayName("a completed step that cannot be undone escalates instead of being retried")
    void irreversibleStepEscalates() {
        task(1L, 1, "PAYOUT", false);
        task(2L, 2, "BOOM", true);
        TaskHandlerRegistry registry = new TaskHandlerRegistry()
                .register(handler("PAYOUT", () -> {
                }))
                .register(handler("BOOM", () -> {
                    throw new IllegalStateException("ledger write rejected");
                }));

        JobOutcome outcome = new SagaRunner(store, registry).run(job());

        assertThat(outcome.state()).isEqualTo(JobState.ESCALATED);
        assertThat(outcome.escalated()).isEqualTo(1);
        assertThat(effects).doesNotContain("undo:1");
        assertThat(store.reload(1L).getState()).isEqualTo(TaskState.ESCALATED);
        assertThat(outcome.reason()).contains("could not be undone");
    }

    @Test
    @DisplayName("two runners racing the same job execute each step exactly once")
    void concurrentRunnersDoNotDoubleExecute() throws Exception {
        for (int step = 1; step <= 20; step++) {
            task(step, step, "POST", true);
        }
        AtomicInteger executions = new AtomicInteger();
        TaskHandlerRegistry registry = new TaskHandlerRegistry()
                .register(handler("POST", executions::incrementAndGet));

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        for (int runner = 0; runner < 2; runner++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    new SagaRunner(store, registry).run(job());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(executions.get()).isEqualTo(20);
        assertThat(store.transitionLog().stream().filter(line -> line.endsWith("CLAIMED>IN_FLIGHT")).count())
                .isEqualTo(20L);
    }
}
