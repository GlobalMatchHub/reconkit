package dev.sellerkit.reconkit.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A line from the counterparty's statement. */
@Entity
@Table(name = "statement_entry")
public class StatementEntry extends TransactionEntry {
}
