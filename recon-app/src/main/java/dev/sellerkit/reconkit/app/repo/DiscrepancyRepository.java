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

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, Long> {

    List<Discrepancy> findByRunIdOrderByIdAsc(Long runId);

    List<Discrepancy> findByStatusInAndCounterpartyIdAndBusinessDateBetween(
            List<DiscrepancyStatus> statuses, Long counterpartyId, LocalDate from, LocalDate to);

    long countByStatusIn(List<DiscrepancyStatus> statuses);

    long countByCounterpartyIdAndBusinessDateBetweenAndStatusIn(
            Long counterpartyId, LocalDate from, LocalDate to, List<DiscrepancyStatus> statuses);

    /**
     * Worst first, then newest.
     *
     * <p>The severity column stores the enum name, so an ordinary DESC sorts it
     * alphabetically and puts MEDIUM above HIGH. The ranking is spelled out instead of
     * relying on the column, because a queue that quietly buries the urgent rows looks
     * exactly like a queue that is working.
     */
    @Query("""
           select d from Discrepancy d
           where (:status is null or d.status = :status)
             and (:severity is null or d.severity = :severity)
             and (:type is null or d.type = :type)
             and (:counterpartyId is null or d.counterpartyId = :counterpartyId)
             and d.businessDate between :from and :to
           order by case d.severity
                      when dev.sellerkit.reconkit.domain.enums.Severity.CRITICAL then 0
                      when dev.sellerkit.reconkit.domain.enums.Severity.HIGH then 1
                      when dev.sellerkit.reconkit.domain.enums.Severity.MEDIUM then 2
                      else 3 end,
                    d.businessDate desc, d.id desc
           """)
    Page<Discrepancy> search(@Param("status") DiscrepancyStatus status,
                             @Param("severity") dev.sellerkit.reconkit.domain.enums.Severity severity,
                             @Param("type") dev.sellerkit.reconkit.domain.enums.DiscrepancyType type,
                             @Param("counterpartyId") Long counterpartyId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             Pageable pageable);

    @Query("""
           select d.severity, count(d) from Discrepancy d
           where d.status in :statuses and d.businessDate between :from and :to
           group by d.severity
           """)
    List<Object[]> countBySeverity(@Param("statuses") List<DiscrepancyStatus> statuses,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
           select d.type, count(d), coalesce(sum(abs(d.deltaMinorUnits)), 0) from Discrepancy d
           where d.status in :statuses and d.businessDate between :from and :to
           group by d.type
           order by count(d) desc
           """)
    List<Object[]> countByType(@Param("statuses") List<DiscrepancyStatus> statuses,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);
}
