# Expenses App — Backend

Java 17 + Spring Boot 3 + MongoDB backend implementing the API needed by the
Expenses App frontend described in `expenses-app-specification.md`. See
`SCHEMA_DESIGN.md` for the MongoDB data model and rationale.

## Running locally

Requires a MongoDB instance reachable at `mongodb://localhost:27017` (or set
`MONGODB_URI`).

```bash
mvn spring-boot:run
```

Or build and run the jar:

```bash
mvn clean package
java -jar target/expenses-app-0.1.0.jar
```

Server listens on port `8080`.

## API summary

| Method | Path | Spec section | Purpose |
|---|---|---|---|
| GET | `/api/years` | 2.A | List year cards |
| POST | `/api/years` `{"year": 2027}` | 2.A | "+ Add Year" |
| DELETE | `/api/years/{year}` | 2.A | Trash-can on a year card (cascades to its transactions) |
| GET | `/api/years/{year}/months` | 2.B | 12 month cards with income/expense totals |
| GET | `/api/years/{year}/months/{month}/transactions` | 2.C | Transaction list table |
| GET | `/api/years/{year}/months/{month}/transactions/balance` | 2.C | "Total Monthly Balance" |
| POST | `/api/years/{year}/months/{month}/transactions/expenses` | 3.A | "Save Expense" (handles installment splitting) |
| POST | `/api/years/{year}/months/{month}/transactions/income` | 3.B | "Save Income" |
| PUT | `/api/years/{year}/months/{month}/transactions/{id}` | 3.C | "Save Expense" (edit) |
| DELETE | `/api/years/{year}/months/{month}/transactions/{id}` | 2.C | "X" delete action |

### Example: adding a variable expense split into 3 installments

```
POST /api/years/2026/months/11/transactions/expenses
{
  "description": "New laptop",
  "value": 1200.00,
  "expenseType": "VARIABLE",
  "installments": 3
}
```

Given a start month of November 2026, this creates three transactions:
`Nov 2026 (1/3)`, `Dec 2026 (2/3)`, `Jan 2027 (3/3)` — auto-creating the
`2027` Year document since the series rolled past December, matching spec
3.A's conditional logic.

## Not yet implemented / left for the frontend or a follow-up

- Auth/multi-user support — the spec doesn't describe users, so all data is
  currently global/single-tenant.
- Currency formatting/localization — values are returned as plain
  `BigDecimal`; "R$" / "$" formatting and 2-decimal display are frontend
  concerns per the spec's UI-only currency mentions.
- Editing an expense's installment count — spec 3.C's Edit modal only shows
  Description/Value/Type fields being edited in place; re-splitting an
  edited installment across months isn't specified, so it's out of scope.
