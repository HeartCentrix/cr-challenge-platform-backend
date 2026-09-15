# 200-question Java bank

Migration `../11_expand_question_bank_to_200.sql` adds **180** distinct Java problems
to the original 20. It does not recreate the original seed, change existing
testcase IDs, delete submissions, or send validation jobs to Judge.

The additions cover arrays (30), strings (29), dynamic programming (30), graphs
(25), grids (20), number theory/combinatorics (25), and advanced data structures,
geometry and greedy algorithms (21). Difficulty metadata ranges from 4 to 8;
the original questions and their difficulty ratings are preserved.

Each addition contains a complete input/output specification, two worked I/O
examples, a Java `Main` starter with input/output helpers, a private reference
solution, and ten deterministic cases (two public, eight hidden). Cases are worth
10 points each. The existing **global ten-minute session** remains unchanged;
no extra question timer, frontend request, or Judge infrastructure is introduced.

## Validate and regenerate

Requirements: Python 3.10+ and JDK 11+ (`java` and `javac` on PATH). No Python
third-party package, AWS credential, or database connection is needed to build.

```powershell
python db/bank200/build_bank.py --workers 4
# Validate without rewriting generated artifacts:
python db/bank200/build_bank.py --check --workers 4
```

The builder checks all 180 Java reference solutions against answers computed by
separate Python implementations: **1,800 executed cases**, not outputs copied
from the Java answer key. It also compiles the shared starter, checks unique
slugs/titles and counts, and targets Java 11 bytecode/language compatibility.
Compilation files live in an automatically cleaned temporary directory.
`validation.json` records the migration hash and question inventory. It is a
validation manifest, not an application runtime dependency.

Expected output comparisons ignore whitespace between tokens. Examples are
derived from the same validated cases. These deterministic checks are not a
formal correctness proof or an exhaustive performance benchmark.

## Apply

Review and run the generated SQL as the **challenge_platform database owner**,
on local/dev as required. Do not run against a CodeReport application database.
Pass credentials through your normal secure connection, never SQL or source files.

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d challenge_platform -v ON_ERROR_STOP=1 -f db/11_expand_question_bank_to_200.sql
```

The migration is transactional and checks the database name and exact original
20 active slugs before inserting. It inserts new questions inactive, adds cases,
then activates them. Existing slugs/cases must match exactly on rerun; conflicts
fail instead of overwriting records. Final checks require 200 active questions,
ten cases per question, two samples and 100 testcase points per question.

No backend redeploy is required after applying: the current database-backed API
already randomly selects from all active questions. Existing issued questions and
saved attempts retain their original content. Hidden cases and reference solutions
remain backend-only; never copy this folder or the migration into frontend assets.

For an intentional rollback, first stop issuing these additions by marking only
their manifest slugs inactive. Do not delete question/testcase rows referenced by
historical attempts. The baseline guard will require review if other bank changes
are made later.
