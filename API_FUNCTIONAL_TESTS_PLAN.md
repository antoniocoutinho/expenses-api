# Functional API Tests Plan — Expenses API

> **Audience:** any LLM (or developer) that must write the functional API test scripts for this project.
> **Goal:** start the real application with a real MongoDB, call the real HTTP endpoints, and check the results.
> **Rule:** follow this plan exactly. If something is unclear or conflicts with the real code, **read the source code first** and mention the difference in your answer. Do not guess.

---

## 1. Project context

- Java 17+, Spring Boot 3.3.x, Maven, Spring Data MongoDB, springdoc-openapi (Swagger).
- Base package: `com.expensesapp`.
- Purpose: track income and expenses by **year → month → transactions**.
- Source of truth for behavior: the production code in `src/main/java/com/expensesapp` (controllers, services, DTOs, models) and the product spec `expenses-app-specification.md`.

### Data model (short)

- `years` collection: one document per year, with a **cached** map of monthly totals (`totalIncome`, `totalExpense`).
- `transactions` collection: one document per line item (`year`, `month`, `description`, `value`, `kind`, `expenseType`, `installments{groupId,index,total}`).
- **Sign rule:** expenses are stored **negative**, income **positive**. The API receives a **positive** value and the server applies the sign.
- `totalExpense` in the month cache is stored as a **positive** number.

---

## 2. Decisions (do not change without a reason)

| Topic | Decision |
|---|---|
| Location | Same Maven project and repository, separate from unit tests |
| Package | `src/test/java/com/expensesapp/functional/` |
| Naming | Classes end with `IT` (example: `YearApiIT`) |
| Runner | JUnit 5 + `@SpringBootTest(webEnvironment = RANDOM_PORT)` |
| Database | Testcontainers with the real `mongo` image |
| HTTP client | REST Assured |
| Assertions | AssertJ for objects, REST Assured `body(...)` for JSON |
| Maven plugin | Maven Failsafe (`mvn verify`); Surefire (`mvn test`) keeps running only unit tests |
| Mocks | **None.** No `@MockBean`, no Mockito in functional tests |

### Why these choices (short)

- Same project: shares DTOs and enums, one pipeline, simple setup.
- `*IT` + Failsafe: fast unit tests stay fast; slow tests run on demand and in CI.
- Testcontainers: same database as production, so queries and indexes behave the same.
- REST Assured: readable tests that look like the spec.
- No mocks: the value of these tests is checking all layers together.

---

## 3. Setup tasks

### 3.1 Dependencies (test scope)

Add to `pom.xml`:

- `org.springframework.boot:spring-boot-testcontainers`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:mongodb`
- `io.rest-assured:rest-assured` (version is normally managed by the Spring Boot BOM; if not, use a 5.x version)

**Remove** the Flapdoodle embedded Mongo dependency (`de.flapdoodle.embed.mongo.spring3x`). It is not needed and can conflict.

### 3.2 Failsafe plugin

- Add `maven-failsafe-plugin` to `<build><plugins>` (version managed by the Spring Boot parent).
- Execute goals `integration-test` and `verify`.
- Include pattern: `**/*IT.java`.
- Surefire must **not** run `*IT` classes (default Surefire patterns already exclude them; verify this).

### 3.3 Base class `AbstractApiIT`

Create one abstract base class that every `*IT` class extends. It must:

1. Start **one** MongoDB container for the whole test run (static, shared). Do not start one per test.
2. Connect the container to Spring (use `@ServiceConnection`, available in Spring Boot 3.1+).
3. Use `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
4. Inject the random port (`@LocalServerPort`) and configure REST Assured `baseURI`, `port`, and `basePath` in a `@BeforeEach`.
5. **Clean the database before each test** (delete all documents in `years` and `transactions`). Use `MongoTemplate` or the repositories.
6. Configure REST Assured to read JSON numbers as `BigDecimal` (use `JsonConfig` with `numberReturnType(BIG_DECIMAL)`), so money is never compared as `double`.
7. Enable REST Assured request/response logging **only when a check fails** (`log().ifValidationFails()`).

### 3.4 Helper class `ApiClient` (recommended)

A small helper with methods such as `createYear`, `createIncome`, `createExpense`, `listTransactions`, `getMonths`, `getBalance`, `updateExpense`, `deleteTransaction`, `deleteYear`.
- Each method sends the request and returns the REST Assured `Response`.
- Tests use it in the **Given** step so scenarios stay short and readable.
- Keep status-code and JSON checks **in the tests**, not in the helper.

### 3.5 Test data builders (recommended)

Small builders for request bodies (`expense`, `income`, `updateExpense`) with sensible defaults, so each test only sets what it cares about.

---

## 4. Test conventions

- **One behavior per test.** Name methods in plain language: `shouldRollInstallmentsIntoNextYear`.
- Use `@DisplayName` with a readable sentence.
- Structure: **Given / When / Then** (use comments if helpful).
- Tests must be **independent** and runnable in any order. Never rely on data from another test.
- Each test creates the data it needs through the API (not by writing directly to Mongo), except cleanup.
- Money: compare with `BigDecimal.compareTo` or AssertJ `isEqualByComparingTo`. Never compare money as `double`.
- Do not use `Thread.sleep`. The API is synchronous.
- Do not assert on timestamps (`createdAt`, `updatedAt`) beyond "not null" where useful.
- Do not assert on generated ids beyond "not blank".
- Group scenarios with `@Nested` classes per feature area.

---

## 5. API reference (as implemented)

Base path: `/api`. JSON in and out.

| Method | Path | Success | Notes |
|---|---|---|---|
| GET | `/api/years` | 200 | List of `{year}` sorted ascending |
| POST | `/api/years` | 201 | Body `{"year": 2027}`. 409 if it exists. 400 if `year` is missing |
| DELETE | `/api/years/{year}` | 204 | Also deletes all transactions of that year. 404 if missing |
| GET | `/api/years/{year}/months` | 200 | Always 12 items. 404 if the year does not exist |
| GET | `/api/years/{year}/months/{month}/transactions` | 200 | Ordered by creation time, oldest first |
| GET | `/api/years/{year}/months/{month}/transactions/balance` | 200 | `{"balance": <sum of signed values>}` |
| POST | `/api/years/{year}/months/{month}/transactions/expenses` | 201 | Returns an **array** (one item per installment) |
| POST | `/api/years/{year}/months/{month}/transactions/income` | 201 | Returns a single object |
| PUT | `/api/years/{year}/months/{month}/transactions/{id}` | 200 | Edits an **expense** in place |
| DELETE | `/api/years/{year}/months/{month}/transactions/{id}` | 204 | 404 if missing |

Utility endpoints: `GET /v3/api-docs` (OpenAPI JSON) and `/swagger-ui.html`.

### Request bodies

- Expense: `description` (not blank), `value` (positive), `expenseType` (`FIXED` or `VARIABLE`), `installments` (integer ≥ 1, default 1).
- Income: `description` (not blank), `value` (positive).
- Update expense: `description` (not blank), `value` (positive), `expenseType`.

### Response fields (transaction)

`id`, `year`, `month`, `description`, `kind` (`INCOME`/`EXPENSE`), `expenseType` (null for income), `value` (signed), `splitLabel` (example `"2/3"`, null when not split), `createdAt`, `updatedAt`.

### Month summary item

`month` (1–12), `monthName` (English, example `"January"`), `totalIncome`, `totalExpense`.

### Error body

For `400`, `404`, `409` handled by the app: JSON with `timestamp`, `status`, `error`, `message`.

---

## 6. Business rules to verify

1. Expenses are stored negative, income positive.
2. `FIXED` expense → always **one** transaction, even if `installments` > 1 is sent.
3. `VARIABLE` with `installments` = 1 → one transaction, `splitLabel` is null.
4. `VARIABLE` with `installments` = N > 1 → N transactions in consecutive months, starting at the requested month; `splitLabel` is `1/N … N/N`.
5. **Installment value:** each installment is the total divided by N, rounded to 2 decimals (example: `1000.00 / 3 = 333.33`). **Confirm this against the current `TransactionService` code before writing assertions.** The product spec does not define it; the implementation does.
6. When installments pass December, they continue in January of the next year, and that year is **created automatically** (also for more than one year boundary).
7. Adding income or expense to a year that does not exist **creates the year automatically** (current behavior).
8. Month totals (`GET .../months`) must always match the transactions:
   - income adds to `totalIncome`;
   - expense adds the **absolute** value to `totalExpense`;
   - edit and delete must **reverse the old value from the same bucket**.
9. Monthly balance = sum of all signed values of that month (0 when empty).
10. Editing does **not** change year, month, installments, or `kind`.
11. Deleting a year removes its transactions and its month summaries.
12. Deleting one installment removes **only that row**.

---

## 7. Scenario catalog

Write at least these scenarios. IDs help traceability; use them in `@DisplayName` or comments.

### 7.1 Smoke

| ID | Scenario | Expected |
|---|---|---|
| SM-01 | Application starts with the Mongo container | Context loads |
| SM-02 | `GET /v3/api-docs` | 200 and contains the API title |

### 7.2 Years (spec 2.A)

| ID | Given / When | Then |
|---|---|---|
| YR-01 | No data, `GET /api/years` | 200, empty array |
| YR-02 | `POST /api/years {"year":2027}` | 201, body `year = 2027` |
| YR-03 | Create 2027, 2025, 2026 (this order), then list | Sorted: 2025, 2026, 2027 |
| YR-04 | Create the same year twice | Second call is 409 |
| YR-05 | `POST /api/years {}` | 400 |
| YR-06 | Create year, then `DELETE` it | 204, year no longer listed |
| YR-07 | `DELETE` a year that does not exist | 404 |
| YR-08 | Year with income + expense, then delete year | 204; recreating the year shows empty months and no transactions (cascade) |

### 7.3 Months (spec 2.B)

| ID | Given / When | Then |
|---|---|---|
| MO-01 | New empty year, `GET .../months` | 12 items, months 1–12 in order, all totals `0` |
| MO-02 | Same | `monthName` of month 1 is `January`, month 12 is `December` |
| MO-03 | Year does not exist | 404 |
| MO-04 | Add income 1500 in month 3 | Month 3 `totalIncome = 1500`, other months `0` |
| MO-05 | Add expense 200 in month 3 | Month 3 `totalExpense = 200` (positive number) |

### 7.4 Income (spec 3.B)

| ID | Given / When | Then |
|---|---|---|
| IN-01 | Valid income | 201, `kind = INCOME`, `value` positive, `expenseType` null, `splitLabel` null |
| IN-02 | Blank description | 400 |
| IN-03 | Missing value | 400 |
| IN-04 | Value `0` or negative | 400 |
| IN-05 | Year does not exist yet | 201 and the year now appears in `GET /api/years` |

### 7.5 Expenses, no installments (spec 3.A)

| ID | Given / When | Then |
|---|---|---|
| EX-01 | `FIXED`, value 1200 | 201, array size 1, `value = -1200`, `kind = EXPENSE`, `splitLabel` null |
| EX-02 | `FIXED` with `installments = 5` | Still array size 1 |
| EX-03 | `VARIABLE` with `installments = 1` | Array size 1, `splitLabel` null |
| EX-04 | Blank description | 400 |
| EX-05 | Value `0` or negative | 400 |
| EX-06 | Missing `expenseType` | 400 |
| EX-07 | Invalid `expenseType` text (example `"FOO"`) | 400 (do not assert the body) |
| EX-08 | `installments = 0` | 400 |

### 7.6 Installments and year rollover (spec 3.A)

| ID | Given / When | Then |
|---|---|---|
| IS-01 | `VARIABLE`, 3 installments, start March 2026 | Months 3, 4, 5 of 2026; labels `1/3`, `2/3`, `3/3` |
| IS-02 | Total 300.00, 3 installments | Each `value = -100.00` |
| IS-03 | Total 1000.00, 3 installments | Each `value = -333.33` |
| IS-04 | 3 installments, start November 2026 | Nov 2026, Dec 2026, Jan 2027 |
| IS-05 | Same as IS-04, then `GET /api/years` | Both 2026 and 2027 are listed |
| IS-06 | Same as IS-04, then `GET /api/years/2027/months` | January has `totalExpense` equal to one installment |
| IS-07 | 15 installments, start November 2026 | Last item is January 2028; 2027 has 12 items |
| IS-08 | 3 installments, start October 2026 | All three stay in 2026 (exact fit, no rollover) |
| IS-09 | Two separate installment purchases | Items of each purchase are independent (use `splitLabel` and months to check) |
| IS-10 | After creating installments, list each affected month | Each month contains exactly one row of that purchase |

### 7.7 Listing and balance (spec 2.C)

| ID | Given / When | Then |
|---|---|---|
| LB-01 | Create A, then B in the same month, list | Order is A, B |
| LB-02 | Month with income 2000 and expense 300 | Balance `1700` |
| LB-03 | Month with no transactions | Balance `0`, list is empty |
| LB-04 | Transactions in month 3 only | Listing month 4 returns empty array |

### 7.8 Edit expense (spec 3.C)

| ID | Given / When | Then |
|---|---|---|
| ED-01 | Edit description, value, type | 200, new values, value is negated, same `year` and `month` |
| ED-02 | Edit an expense of 50 to 65 | Month `totalExpense` is `65` (**regression test**: old value must be removed from the expense bucket, income stays `0`) |
| ED-03 | Edit changes the balance | Balance reflects the new value |
| ED-04 | Edit unknown id | 404 |
| ED-05 | Edit with value `0` | 400 |
| ED-06 | Edit one installment row | Only that row changes; other rows of the same purchase stay the same |

### 7.9 Delete transaction (spec 2.C)

| ID | Given / When | Then |
|---|---|---|
| DL-01 | Delete an expense | 204, no longer in the list |
| DL-02 | Delete an expense of 15 | Month `totalExpense` goes back to `0` (**regression test**) and `totalIncome` is not increased |
| DL-03 | Delete an income of 100 | Month `totalIncome` goes back to `0` |
| DL-04 | Delete unknown id | 404 |
| DL-05 | Delete one installment of three | Other two remain, with their labels unchanged |

### 7.10 End-to-end journey

| ID | Scenario |
|---|---|
| E2E-01 | Create year → add income → add fixed expense → add 3-installment variable expense that crosses into next year → check months of both years → edit one expense → delete one transaction → verify balance and all month totals at each step → delete the year → verify everything is gone |

---

## 8. Known behaviors and gaps (do NOT assert on these as "correct")

These exist today. Do not write tests that lock them in as desired behavior. If you want to cover them, mark the test `@Disabled("known gap: ...")` with a short reason.

- The `month` path value is **not validated** (`0`, `13`, negative are accepted).
- `@Min` on path variables is not enforced.
- `PUT .../transactions/{id}` always treats the transaction as an **expense** (it negates the value). Editing an **income** through this endpoint would turn it into a negative value. Do not test income edit.
- Installment rounding can leave a small difference from the total (example: 3 × 333.33 = 999.99). The leftover cent is not assigned to any installment.
- Malformed JSON or wrong types return Spring's default 400 body, not the app's custom error body. Assert only the status.
- No authentication and no multi-user data separation.

---

## 9. How to run

| Command | What it runs |
|---|---|
| `mvn test` | Unit tests only (fast, no Docker) |
| `mvn verify` | Unit tests + `*IT` functional tests (needs Docker) |
| `mvn verify -Dit.test=YearApiIT` | One functional test class |

CI notes:
- The CI agent must have Docker available.
- Run `mvn test` on every commit and `mvn verify` on every pull request.
- Failsafe reports go to `target/failsafe-reports`.

---

## 10. Suggested file layout

```
src/test/java/com/expensesapp/functional/
├── AbstractApiIT.java        # container, Spring Boot test setup, cleanup, REST Assured config
├── support/
│   ├── ApiClient.java        # one method per endpoint
│   └── RequestBuilders.java  # default request bodies
├── SmokeIT.java
├── YearApiIT.java
├── MonthApiIT.java
├── IncomeApiIT.java
├── ExpenseApiIT.java
├── InstallmentApiIT.java
├── TransactionListApiIT.java
├── TransactionEditApiIT.java
├── TransactionDeleteApiIT.java
└── UserJourneyIT.java
```

---

## 11. Definition of done

- [ ] All scenarios in section 7 are implemented (or explicitly skipped with a reason).
- [ ] No mocks are used in functional tests.
- [ ] Tests pass with `mvn verify` on a clean machine that has Docker.
- [ ] Tests do not depend on execution order.
- [ ] Money is never compared as `double`.
- [ ] Flapdoodle dependency is removed.
- [ ] `mvn test` still runs only unit tests.
- [ ] Any difference between this plan and the real code is written in the final answer.

---

## 12. Instructions for the LLM (read before writing code)

1. Read the production code and this plan first. Do not invent endpoints, fields, or rules.
2. Create the setup (section 3) before the scenarios.
3. Implement scenarios in the order of section 7. Compile and run after each group if you can.
4. Keep tests short, readable, and independent.
5. If a scenario fails because of a **bug in the production code**, do not change the expectation to hide it. Report the bug clearly (expected vs actual) and, if asked, propose a fix separately.
6. Do not change production code unless the user asks for it.
7. At the end, list: files created, scenarios implemented, scenarios skipped (with reasons), and any assumption you made.
