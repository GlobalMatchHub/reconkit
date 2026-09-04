# Design notes

Longer form notes on the parts of Reconkit that took the most thought. The README covers
what the system does; this covers how it is put together and what was rejected.

## Data model

```
tenant ── counterparty ── settlement_terms (embedded: rate, flat fee, tax, cycle,
       │                                    holdback, cutoff, tolerances, match window)
       │
       ├── ingest_batch ── ledger_entry     (our books)
       │                └─ statement_entry  (theirs)
       │
       ├── recon_run ── match_group ── match_member
       │             └─ discrepancy
       │
       ├── reprocess_job ── reprocess_task
       ├── settlement ── settlement_line
       ├── settlement_lock
       └── audit_log
```

Both sides of the reconciliation share one mapped superclass. Keeping the two schemas
identical is deliberate: every place they are allowed to drift apart becomes a special case
inside a matching rule, and normalisation then happens repeatedly inside the matcher
instead of once at load time.

`tenant_id` is the first column of every unique key and of every index a query filters on.
Hibernate's `@TenantId` appends the predicate to every statement, so an index that does not
lead with it forces the optimiser to filter after the fact on the largest tables in the
system.

## Isolation

Tenancy is a discriminator on the mapped superclass, resolved per request from the access
token by a `CurrentTenantIdentifierResolver`. No repository method mentions the tenant.
That is the property worth having: the query that forgets the filter is always the one
written in a hurry at the end of a release, and here there is nothing to forget.

The thread local holding the tenant is cleared in a `finally` block. Servlet threads are
pooled, and a thread that finishes still holding a tenant hands it to whoever lands on it
next.

`app_user` is the exception. Login happens before a tenant is known, so the filter would
have nothing to apply; the tenant is a plain column there and is what seeds the context
after a successful sign in.

## The run

A run is anchored to one statement date and reaches across a window on our own side.

That direction matters and was the second attempt. Widening the statement side pulls the
same line into every run in the window: it is matched on the day it belongs to and reported
missing on the four around it, and the totals inflate by roughly the width of the window,
so the reported match rate depends on how wide the window is rather than on how well the
day reconciled. Widening our side instead is also the truer model. The counterparty's file
is what arrives tonight and has to be signed off tonight; our ledger is already there and
can be searched backwards.

The consequence is that a transaction we booked at 23:55 whose line arrives on tomorrow's
file is reported missing tonight, correctly, and closed by tomorrow's run. Auto closing is
recorded against the run that did it, never silent.

```
load statements for D, ledger for D-w .. D+w
  detect duplicates on each side, before any matching
  pass A  exact transaction id
  pass B  approval + amount, then order id + amount
  pass C  amount within tolerance, time within window, globally scored
  pass D  grouped rows against a single payout line
  classify what is left, scoped to rows whose own business date is D
  close differences from earlier runs that this run has now explained
```

## Concurrency

Four different problems, four different mechanisms, because one lock does not fit them.

| Problem | Mechanism |
|---|---|
| Two schedulers reconciling the same night | unique key on (tenant, counterparty, date, sequence); the loser reads the winner's run |
| Two workers reaching the same reprocess step | conditional `UPDATE ... WHERE state = :from`, proceed only when one row changed |
| Two generations of the same settlement period | `SELECT ... FOR UPDATE` on a lock row, plus a unique key behind it |
| Two operators resolving the same difference | `@Version` optimistic lock, the client sends the version it read |

The optimistic lock is the one that is easiest to leave out and the one whose absence is
invisible: without it the second save silently discards the first operator's resolution
note, and nobody finds out until a counterparty asks why the explanation changed.

Job and task state transitions run in their own transactions, in a separate bean from the
service that calls them. A transactional method called from another method of the same
class never goes through the proxy, the annotation is silently ignored, and the "separate
transaction" is the caller's. That is invisible in review and fatal here, where the whole
design depends on a state change being committed while the job is still running.

## Rejected

**Pushing passes A and B into SQL as joins.** It is the obvious optimisation and the
measurements did not support it: 50,000 rows a side reconcile in memory in well under a
second, and the day that is slow is the day everything falls through to pass C, which
cannot be a join. Keeping the engine a pure function was worth more than the join.

**Subset sum for aggregated payouts.** Exponential, and worse than that, wrong: with enough
small transactions some subset always adds up and the matcher starts inventing groupings.

**One tolerance number.** Covered above. Two knobs, absolute and relative.

**Deriving compensation at rollback time.** By the time a rollback runs, the state it would
derive the answer from is exactly what is wrong. The compensation is recorded when the task
is created.

**Retrying an irreversible step.** If money has already moved, retrying the reversal does
not bring it back, and an automatic retry loop against an external system turns one failure
into a series of them.

## Performance

The engine is measured in `ReconEngineScaleTest`: 50,000 rows a side, and a second case
where the identifier column is unusable so the entire day falls through to the scoring
pass. Both finish in seconds on a laptop.

The demo seeder is the slow part, not the engine. It writes about 44,000 rows through JPA
with identity keys, which disables JDBC batching, and takes a few minutes. Real ingest goes
through the same path; a production load would use a batch insert, and the schema is
already shaped for it.

## What a subscription product would need next

The tenant model, the per counterparty contract terms and the module boundaries are already
the right shape. Missing: metering and billing, a fee schedule that is a table rather than a
field (card brand, instalment count, merchant tier), per tenant column mapping profiles
stored in the database rather than in code, and a holiday calendar per country. None of
those change the engine.
