package dev.sellerkit.reconkit.app.repo;

import dev.sellerkit.reconkit.domain.enums.DiscrepancyStatus;
import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.enums.TaskState;
import dev.sellerkit.reconkit.domain.model.AppUser;
import dev.sellerkit.reconkit.domain.model.AuditLog;
import dev.sellerkit.reconkit.domain.model.Counterparty;
import dev.sellerkit.reconkit.domain.model.Discrepancy;
import dev.sellerkit.reconkit.domain.model.IngestBatch;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.MatchGroup;
import dev.sellerkit.reconkit.domain.model.MatchMember;
import dev.sellerkit.reconkit.domain.model.ReconRun;
import dev.sellerkit.reconkit.domain.model.ReprocessJob;
import dev.sellerkit.reconkit.domain.model.ReprocessTask;
import dev.sellerkit.reconkit.domain.model.Settlement;
import dev.sellerkit.reconkit.domain.model.SettlementLine;
import dev.sellerkit.reconkit.domain.model.SettlementLock;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import dev.sellerkit.reconkit.domain.model.Tenant;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReprocessTaskRepository extends JpaRepository<ReprocessTask, Long> {

    List<ReprocessTask> findByJobIdOrderByStepNoAsc(Long jobId);

    /**
     * The conditional transition.
     *
     * <p>This single statement is what makes reprocessing safe under concurrency. The
     * {@code state = :from} predicate means two workers that both believe a task is
     * claimable will both issue the update and exactly one will affect a row. An
     * idempotency key cannot do this job: it stops a duplicate request from creating
     * a second job, and says nothing about a second worker reaching a task that is
     * already in flight.
     *
     * @return number of rows changed, which is 1 for the winner and 0 for everyone else
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update ReprocessTask t
              set t.state = :to,
                  t.claimedAt = case when :to = dev.sellerkit.reconkit.domain.enums.TaskState.CLAIMED
                                     then :now else t.claimedAt end
            where t.id = :id and t.state = :from
           """)
    int transition(@Param("id") Long id,
                   @Param("from") TaskState from,
                   @Param("to") TaskState to,
                   @Param("now") java.time.Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReprocessTask t set t.attempt = t.attempt + 1, t.lastError = :error where t.id = :id")
    int recordAttempt(@Param("id") Long id, @Param("error") String error);
}
