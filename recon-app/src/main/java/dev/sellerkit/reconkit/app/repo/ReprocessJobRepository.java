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

public interface ReprocessJobRepository extends JpaRepository<ReprocessJob, Long> {

    Optional<ReprocessJob> findByIdempotencyKey(String idempotencyKey);

    List<ReprocessJob> findTop100ByOrderByIdDesc();
}
