# BetterTeams-modern — Agent Instructions

## Project Goal

BetterTeams-modern is a Minecraft Paper plugin.

The primary goal is to maintain a small, reliable, understandable codebase with minimal architectural complexity.

**Prioritize simplicity, correctness, data integrity, and backward compatibility over abstraction and extensibility.**

Do not rewrite working systems without a demonstrated benefit.

## 1. Architecture Rules

- Prefer simple, direct Java code.
- Avoid unnecessary interfaces, factories, services, repositories, wrappers, and utility classes.
- Do not introduce frameworks or dependencies without a clear need.
- Avoid excessive delegation and deeply layered architectures.
- Prefer deleting obsolete code over preserving unused abstractions.
- Do not introduce speculative future-proofing.
- Keep classes focused on clear responsibilities.
- Do not move complexity into a different class and call it simplification.

A refactor is successful when the code becomes easier to understand, not merely shorter.

## 2. Team State

- There must be one authoritative source of mutable team state.
- Avoid duplicate mutable state between `Team`, `TeamData`, and storage classes.
- Keep team mutations predictable and validated.
- Avoid exposing internal mutable collections.
- Prefer defensive copies when necessary.
- Use one consistent mechanism for tracking persistence changes.
- Do not add multiple overlapping dirty-state mechanisms.
- Keep persistence logic out of the domain model wherever practical.

## 3. Threading

Target standard Paper servers unless explicitly instructed otherwise.

- Mutable team state is owned by the Bukkit primary thread.
- Bukkit API interactions must respect Paper thread-safety requirements.
- Database I/O must not block the main thread during normal operation.
- Async database workers must operate on immutable snapshots.
- Never read live Bukkit inventories or mutable team objects from database threads.
- Avoid unnecessary locks and synchronization when ownership is already guaranteed.
- Do not introduce custom concurrency frameworks.
- Do not silently reschedule operations to conceal thread-safety violations.

Thread safety must be explicit and understandable.

## 4. Persistence

SQLite persistence must follow a simple pipeline:

1. Mutate team state on the primary thread.
2. Mark the team dirty.
3. Capture an immutable snapshot on the primary thread.
4. Write the snapshot asynchronously using a controlled database executor.
5. Handle write failures without silently losing changes.
6. Flush pending state safely during shutdown.

Additional rules:

- Do not duplicate live team state inside persistence classes.
- Avoid redundant caches.
- Avoid unnecessary serialization.
- Preserve compatibility with existing databases.
- Prevent stale snapshots from overwriting newer data.
- Ensure team deletion cannot accidentally resurrect deleted teams.
- Handle database failures and shutdown explicitly.
- Never clear pending changes without a valid recovery path.
- Keep the persistence lifecycle understandable without tracing many unrelated classes.

## 5. Economy

- All monetary values must be finite and non-negative.
- Validate transaction amounts before modifying balances.
- Centralize team balance operations.
- Prevent negative balances and arithmetic overflow.
- Handle Vault failures and partial transactions explicitly.
- Do not assume external economy operations are atomic.
- Log failures that may require administrator intervention.

Do not introduce an elaborate transaction framework.

## 6. Inventory Handling

- Keep team enderchest handling minimal.
- Track viewers only when necessary.
- Persist inventory changes reliably.
- Handle inventory closing, quitting, team deletion, and shutdown.
- Avoid serializing inventories on asynchronous threads.
- Do not introduce redundant inventory state or unnecessary event listeners.

## 7. Compatibility

Preserve existing behavior unless a task explicitly authorizes changing it.

Protect:

- Existing SQLite databases and migrations.
- Configuration files and defaults.
- Commands and permissions.
- Team data and player memberships.
- Public APIs used by other plugins.
- Relevant plugin integrations.

Do not remove public methods merely because they are unused internally.

If compatibility requires a small deprecated adapter, prefer that over breaking integrations.

## 8. Code Style

- Use straightforward, readable Java.
- Follow existing project conventions unless they cause real problems.
- Prefer descriptive names over clever abbreviations.
- Keep methods focused.
- Avoid unnecessary helper methods that only wrap one line.
- Avoid excessive comments explaining obvious code.
- Comment important invariants and non-obvious behavior.
- Avoid wildcard imports.
- Remove unused imports and dead code.
- Do not add dependencies for functionality already provided by Java or Paper.

## 9. Refactoring Rules

When refactoring:

1. Understand the existing behavior first.
2. Identify the actual source of complexity.
3. Prefer the smallest change that removes that complexity.
4. Remove obsolete code made unnecessary by the change.
5. Preserve behavior and persistence compatibility.
6. Add or update tests for changed behavior.
7. Run the relevant build and tests.
8. Report remaining risks.

Avoid unrelated cleanup during targeted bug fixes.

Do not perform large rewrites when a smaller change achieves the same result.

## 10. Testing and Verification

Before reporting completion:

- Run the Maven build or relevant test commands.
- Verify compilation.
- Run available automated tests.
- Add regression tests for meaningful bug fixes.
- Check database loading and persistence when affected.
- Check shutdown and failure recovery when affected.
- Review changed code for thread-safety violations.
- Check for accidental API or configuration incompatibilities.

Never claim tests passed unless they actually ran successfully.

If testing is unavailable, explicitly state what was not verified.

## 11. Working Style

- Prefer incremental, reviewable changes.
- Keep diffs focused.
- Avoid introducing abstractions without clear justification.
- Do not add TODOs instead of completing straightforward work.
- Do not suppress exceptions merely to make code appear stable.
- Do not swallow database errors.
- Do not replace working code with placeholders.
- Do not remove features to simplify the implementation.
- Do not modify unrelated files without a concrete reason.

## 12. Definition of Done

A task is complete when:

- The requested behavior works.
- Existing functionality is preserved.
- No unnecessary complexity was introduced.
- Obsolete code has been removed where safe.
- Persistence remains reliable.
- Thread ownership remains clear.
- Relevant tests pass, or limitations are reported.
- The changes can be explained concisely.

## Core Principle

**Prefer the simplest implementation that is demonstrably correct.**

Do not optimize for maximum abstraction, maximum flexibility, or minimum line count.

Optimize for long-term maintainability by a developer unfamiliar with the codebase.