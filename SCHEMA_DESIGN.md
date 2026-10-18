# MongoDB Schema Design — Expenses App

## Design rationale

The UI is Year → Month → Transaction list, and installments (spec 3.A) can
spawn a transaction into future months and even *create a new year* if the
installments run past December. That "spans arbitrary months/years" behavior
is much easier to implement against a **flat, queryable transaction
collection** than against deeply nested year→month→transaction documents
(nesting would force expensive whole-document rewrites every time an
installment touches a different year document).

So the model is:

- `years` — one tiny document per year, mostly a existence marker + cache of
  monthly totals for the Month View grid (avoids recomputing aggregates from
  scratch on every dashboard load; it's a derived/denormalized cache, kept in
  sync when transactions change).
- `transactions` — one document per line item (income or expense occurrence).
  A 3-installment expense produces **3 transaction documents**, linked by a
  shared `installmentGroupId`, each stamped with its own `year`/`month`.

This keeps "Add Expense with installments" a matter of inserting N documents
(one per affected month), which naturally supports rolling into a new year
(spec: *"If installments exceed remaining months of current year, create a
new year and update the months of the new year"*) — we just insert documents
with `year = currentYear + 1` and ensure a `years` doc exists for that year.

## Collection: `years`

```json
{
  "_id": ObjectId,
  "year": 2026,                 // unique index
  "createdAt": ISODate,
  "months": {
    // cached rollups per month, 1-12, recomputed on write-through
    "1":  { "totalIncome": 1500.00, "totalExpense": 320.50 },
    "2":  { "totalIncome": 0,       "totalExpense": 0 }
    // ...sparse; absent month = "0.00 / 0.00" per spec 2.B
  }
}
```

Indexes:
- `{ year: 1 }` unique

Notes:
- `months` is a map keyed by month number as string (Mongo requires string
  keys in embedded docs) — 1–12.
- This cache is *derived*; source of truth is always `transactions`. It's
  updated transactionally alongside transaction writes so the Month View
  grid (spec 2.B) never needs an on-the-fly aggregation for the common case.
  A recomputation endpoint/job can rebuild it from `transactions` if it ever
  drifts.

## Collection: `transactions`

```json
{
  "_id": ObjectId,
  "year": 2026,
  "month": 3,                          // 1-12
  "description": "Netflix",
  "value": -15.99,                     // signed: negative = expense, positive = income
  "kind": "EXPENSE",                   // "EXPENSE" | "INCOME"
  "expenseType": "FIXED",              // "FIXED" | "VARIABLE" | null (null for income)
  "installments": {                    // null for income / fixed expenses
    "groupId": "6613f...-uuid",        // shared across all installments of one purchase
    "index": 1,                        // 1-based: "1/3"
    "total": 3
  },
  "createdAt": ISODate,
  "updatedAt": ISODate
}
```

Indexes:
- `{ year: 1, month: 1 }` — primary access pattern (Month View / Transaction List)
- `{ "installments.groupId": 1 }` — to edit/delete an entire installment
  series together if ever needed (spec only requires editing single rows,
  but this keeps the option open and helps cascading deletes)

Field notes mapped to spec:
- **Description** (3.A/3.B fields) → `description`
- **Type: "Fixed"/"Variable"** (2.C column) → `expenseType`; absent/`null`
  for income rows (income has no type per spec 3.B, which only asks for
  Description + Value)
- **Split? "1/3" or blank** (2.C column) → derived at read time from
  `installments.index`/`installments.total`; blank when `installments` is
  null
- **Value, colored by sign** (2.C column) → `value`, sign convention
  encodes income vs expense so "Total Monthly Balance" (2.C footer) is a
  trivial `$sum`
- **Year rollover for installments** (3.A conditional logic) → handled at
  write time: the service computes `(year, month)` for each installment
  index, incrementing year when month rolls past 12, and upserts the
  `years` document(s) it touches

## Why not nest transactions under year/month documents?

- Editing/deleting a single transaction (spec 2.C actions) would require
  positional array updates into a specific year → month path — doable, but
  it turns simple CRUD into path-sensitive updates.
  transaction is a flat filter by `_id`.
- Installments crossing a year boundary would require an atomic-ish
  multi-year, multi-document update anyway (a new year doc might not exist
  yet) — so cross-document writes are unavoidable regardless of nesting.
  Given that, a flat collection is strictly simpler.
- The Month View grid's totals (2.B) are exactly the kind of read-mostly,
  slow-changing aggregate that benefits from the `years.months` cache,
  while the Transaction List (2.C) benefits from flat per-transaction
  documents. Splitting into two collections lets each optimize for its
  actual access pattern.
