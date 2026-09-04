package dev.sellerkit.reconkit.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Our own record of a transaction. */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry extends TransactionEntry {
}
