# AGENTS.md

## Project: Anon Chat App — Kotlin

This file defines the permanent rules and workflow for AI coding agents working on this repository.

The agent MUST read and follow this file before making changes.

---

# 1. Core Objective

Build and maintain the anonymous chat application according to the project's:

* Product Requirements Document (PRD)
* Implementation specifications
* Existing architecture
* Existing database schema
* Existing coding conventions
* Security and privacy requirements

When implementing a feature, the agent must prefer modifying the existing architecture over introducing unnecessary new systems.

The goal is:

> **Simple, secure, efficient, maintainable, production-ready Kotlin code.**

Do not implement features based only on assumptions. Inspect the repository and relevant project documentation first.

---

# 2. Mandatory Repository Inspection

Before implementing or modifying anything, inspect the repository structure.

At minimum, determine:

1. Android/Kotlin project structure
2. Gradle configuration
3. Application module(s)
4. Package structure
5. Current architecture
6. Navigation implementation
7. UI framework and design system
8. State management
9. Dependency injection
10. Networking layer
11. Supabase integration
12. Database-related code
13. Authentication/identity logic
14. Local persistence
15. Existing tests
16. Existing documentation
17. Existing PRD/specification files

Do not recreate something that already exists.

If an existing implementation can be extended safely, extend it instead of creating a parallel implementation.

---

# 3. ALWAYS Check Installed Skills

Before starting substantial implementation work, inspect the available agent skills/tools.

The agent should determine whether an installed skill is relevant to the task.

Examples:

* Android development
* Kotlin
* Supabase
* Database design
* Security
* Testing
* UI/UX
* Git
* Architecture
* Documentation
* Performance optimization

If a relevant skill is installed, read and follow its instructions before implementing the corresponding part of the task.

Do not ignore installed skills simply because the task appears straightforward.

### Skill workflow

1. Discover available skills.
2. Identify relevant skills.
3. Read the relevant skill instructions.
4. Apply the instructions.
5. Continue implementation.

If multiple skills are relevant, use all applicable skills rather than arbitrarily choosing one.

---

# 4. ALWAYS Use Context7 for Library Documentation

When working with a framework, SDK, library, API, or dependency where current documentation matters, use **Context7** before implementation.

This is especially important for:

* Kotlin
* Kotlin Coroutines
* Jetpack Compose
* AndroidX
* Navigation
* Room
* DataStore
* WorkManager
* Supabase
* Supabase Kotlin SDK
* PostgreSQL
* Ktor
* Serialization
* Authentication libraries
* Dependency injection libraries
* Testing frameworks
* Any third-party dependency

Do not rely solely on remembered API syntax.

### Context7 workflow

Before using an unfamiliar or potentially changed API:

1. Identify the library/package.
2. Resolve the library through Context7.
3. Retrieve the relevant documentation.
4. Verify the API/version currently used by the project.
5. Implement using the documented approach.

If the repository already pins a specific dependency version, prioritize documentation applicable to that version.

Do not blindly copy examples from documentation if they conflict with the project's architecture or dependency versions.

---

# 5. Do Not Guess APIs

Never invent:

* Kotlin APIs
* Supabase APIs
* SQL syntax
* Android APIs
* Gradle configuration
* Compose APIs
* SDK methods
* Dependency names
* Configuration properties

If uncertain, verify using:

1. Existing project code
2. Installed skills
3. Context7
4. Official documentation

Only then implement.

---

# 6. Follow the Existing Architecture

Before introducing architectural changes, identify the architecture currently used by the project.

For example:

```text
UI
 ↓
ViewModel / State Holder
 ↓
Use Case / Domain Logic
 ↓
Repository
 ↓
Data Source
 ↓
Supabase / Local Storage
```

Do not introduce a new architectural pattern for a single feature unless there is a clear technical reason.

Maintain consistency with existing code.

If the project uses:

* MVVM → continue using MVVM
* Clean Architecture → follow its existing layers
* Repository pattern → use the existing repositories
* Hilt/Koin → use the existing DI framework
* Compose → do not introduce XML unnecessarily
* Kotlin Flow → prefer the established reactive state mechanism

Consistency is more important than architectural novelty.

---

# 7. Efficient Coding Principles

Write the smallest implementation that correctly satisfies the requirement.

Prefer:

* Reusable components
* Existing utilities
* Existing repositories
* Existing models
* Existing state holders
* Existing services
* Existing extension functions

Avoid unnecessary:

* Abstractions
* Wrappers
* Interfaces with only one implementation
* Helper classes that are used once
* Duplicate models
* Duplicate API calls
* Duplicate state
* Excessive callbacks
* Excessive dependency additions

Do not over-engineer.

### Example

Bad:

```kotlin
interface UserIdGenerator
class DefaultUserIdGenerator : UserIdGenerator
class UserIdGenerationService(
    private val generator: UserIdGenerator
)
```

when the project only needs:

```kotlin
fun generatePublicId(): String
```

Use the simpler solution unless abstraction provides a real architectural benefit.

---

# 8. Minimize Dependencies

Do not add a dependency unless it provides meaningful value.

Before adding a dependency:

1. Check whether the project already contains an equivalent solution.
2. Check whether Android/Kotlin standard libraries can solve the problem.
3. Check whether an existing project dependency can solve it.
4. Verify the dependency through Context7 or official documentation.
5. Consider APK size, maintenance, security, and compatibility.

Do not add libraries for trivial functionality.

---

# 9. Supabase Rules

Supabase is a core backend of this project.

Treat backend security as a first-class requirement.

Always consider:

* Row Level Security (RLS)
* Database constraints
* Server-side validation
* Authentication
* Authorization
* Anonymous identity
* Public identifiers
* Account deletion
* Data cleanup
* Realtime subscriptions
* Database functions
* Edge Functions when appropriate
* Race conditions
* Duplicate records
* Client tampering

Never assume that client-side validation is sufficient.

Anything security-sensitive must be enforced server-side.

---

# 10. Identity and Public ID Rules

The application uses a device-bound anonymous identity.

The public user identifier is:

* Exactly **8 characters**
* Alphanumeric
* Intended for public display
* Not the same as the internal database primary key
* Not intended to expose sensitive internal identifiers

The agent must preserve the distinction between:

```text
Internal User Identity
        ↓
Database Primary Key / Auth Identity
        ↓
Public Anonymous ID
        ↓
Displayed in the application
```

The public ID must have a single authoritative source.

If the project's specification states that the public ID is server/database generated, the client must not independently generate a competing ID.

Avoid mechanisms that can create:

```text
App Public ID != Database Public ID
```

Identity synchronization must be deterministic and authoritative.

---

# 11. Security-Sensitive Logic

The following should generally NOT rely solely on client-side logic:

* User identity
* Public ID ownership
* Authorization
* Account deletion authorization
* Message ownership
* Blocking
* Reporting
* Rate limits
* Abuse prevention
* Database permissions
* Data visibility
* Moderation restrictions
* Server timestamps
* Security-sensitive state transitions

The client can provide UX validation, but the backend must enforce the actual rule.

---

# 12. Database Design

Prefer database-enforced integrity.

Use:

* Primary keys
* Foreign keys
* Unique constraints
* Check constraints
* Not-null constraints
* Appropriate indexes
* RLS policies
* Database functions/triggers when justified

Avoid enforcing important invariants exclusively in Kotlin.

Example:

If a public ID must be unique:

```sql
UNIQUE(public_id)
```

should be preferred over relying only on:

```kotlin
if (!exists(publicId)) {
    create(publicId)
}
```

because the client-side approach can suffer from race conditions.

---

# 13. Concurrency and Race Conditions

Assume multiple clients or requests can execute simultaneously.

Pay particular attention to:

* Public ID generation
* Account creation
* Message sending
* Conversation creation
* Blocking
* Reporting
* Account deletion
* Cleanup operations
* Realtime state

Do not rely on:

```text
check → then insert
```

when a database constraint or atomic operation can enforce the invariant.

Prefer atomic server/database operations.

---

# 14. State Management

Avoid unnecessary mutable state.

Prefer the project's established Kotlin/Compose state approach.

For example:

```kotlin
StateFlow
MutableStateFlow
UiState
```

where appropriate.

Avoid having the same piece of state represented simultaneously in:

* Activity
* Fragment
* ViewModel
* Repository
* Composable local state

unless there is a clear reason.

There should be a clear source of truth.

---

# 15. Kotlin Standards

Use idiomatic Kotlin.

Prefer:

* `val` over `var`
* Immutable collections where practical
* Sealed classes/interfaces for finite states
* Data classes for state/data models
* Extension functions where they improve readability
* Coroutines for asynchronous work
* Structured concurrency
* Null safety
* Early returns when they improve clarity

Avoid:

* `!!` unless genuinely safe and justified
* Blocking calls on the main thread
* Global mutable state
* Magic numbers
* Long functions
* Giant ViewModels
* God classes
* Nested callback chains

---

# 16. Jetpack Compose Standards

When using Compose:

* Keep composables focused.
* Hoist state appropriately.
* Avoid unnecessary recomposition.
* Keep business logic out of UI composables.
* Use stable state models where appropriate.
* Reuse existing design components.
* Follow the project's existing Material/design system.
* Avoid creating duplicate UI components.

Prefer:

```kotlin
Screen(
    uiState = state,
    onAction = viewModel::onAction
)
```

over placing backend/database/business logic directly inside composables.

---

# 17. Error Handling

Errors should be explicit and user-safe.

Separate:

```text
Technical error
        ↓
Domain/application error
        ↓
User-facing message
```

Do not expose:

* Database internals
* SQL errors
* Stack traces
* Supabase implementation details
* Sensitive identifiers
* Authentication tokens

to users.

Log useful diagnostic information only where appropriate.

Never log secrets.

---

# 18. Logging and Secrets

Never commit or expose:

* Supabase service-role keys
* Private API keys
* Access tokens
* Refresh tokens
* Passwords
* Encryption keys
* Signing secrets

Client applications must only contain keys that are explicitly intended for client use.

Never hardcode secrets into source code.

Check `.gitignore` and local configuration before adding credentials.

---

# 19. Privacy

This is an anonymous chat application.

Treat privacy as a core product requirement.

Do not unnecessarily collect, store, expose, or log:

* Real names
* Email addresses
* Phone numbers
* Device identifiers
* IP addresses
* Location
* Private metadata
* Authentication tokens

If information is not required for the feature, do not introduce it.

---

# 20. Account Deletion

Account deletion must be treated as a complete lifecycle operation, not simply deleting one row.

When modifying deletion behavior, inspect:

* Auth identity
* User profile
* Public identity
* Messages
* Conversations
* Reports
* Blocks
* Realtime-related records
* Related foreign keys
* Storage
* Scheduled jobs
* Database functions
* Cascading behavior

The implementation should avoid orphaned or unintentionally retained user data according to the project's privacy requirements.

Prefer database-level cascading or controlled server-side cleanup where appropriate.

---

# 21. Realtime

When using Supabase Realtime:

* Avoid duplicate subscriptions.
* Unsubscribe when the lifecycle ends.
* Avoid unnecessary channel creation.
* Handle reconnects.
* Handle duplicate events safely.
* Keep UI state consistent with server state.

Do not assume a realtime event is guaranteed to arrive exactly once.

Design consumers to tolerate repeated or delayed events when necessary.

---

# 22. Performance

Optimize based on actual needs.

Prefer:

* Efficient database queries
* Proper indexes
* Pagination
* Lazy UI lists
* Avoiding unnecessary network calls
* Avoiding duplicate requests
* Caching where appropriate
* Batched operations where appropriate

Do not prematurely optimize code that has no measurable problem.

At the same time, avoid obviously expensive patterns such as:

```text
load entire message history
→ render everything
→ repeatedly query database inside loops
```

---

# 23. Testing

When implementing meaningful functionality, consider tests at the appropriate level.

Prioritize testing:

* Identity logic
* Public ID generation/validation
* Authentication flows
* Database interactions
* Authorization
* Message sending
* Account deletion
* State transitions
* Edge cases
* Error handling

Use the project's existing testing framework.

Do not add a new testing framework without justification.

---

# 24. Validation Before Completion

Before considering a task complete:

### Code

* Compile the project.
* Resolve compiler errors.
* Resolve obvious warnings introduced by the change.
* Check imports.
* Check nullability.
* Check coroutine usage.

### Architecture

* Confirm the implementation follows the existing architecture.
* Confirm no unnecessary duplicate logic was introduced.
* Confirm state ownership is clear.

### Backend

* Verify Supabase queries.
* Verify RLS.
* Verify constraints.
* Verify authorization.
* Verify database migrations.

### UI

* Check loading states.
* Check empty states.
* Check error states.
* Check success states.
* Check navigation behavior.

### Security

* Check that client input cannot bypass backend rules.
* Check that sensitive data is not exposed.
* Check that secrets are not committed.

### Regression

Consider whether the change affects:

* Authentication
* Identity
* Chat
* Realtime
* Notifications
* Account deletion
* Navigation
* Database integrity

---

# 25. Database Migration Rules

Whenever the schema changes:

1. Create a proper migration.
2. Do not manually modify production state as a substitute.
3. Include constraints and indexes required by the feature.
4. Consider existing data.
5. Consider rollback/recovery where practical.
6. Update application models/repositories accordingly.
7. Verify RLS policies.
8. Verify foreign-key behavior.

Never make application code assume a schema change that has not been represented in the project's migration system.

---

# 26. Documentation

Update documentation when a change modifies:

* Architecture
* Database schema
* Authentication
* Identity behavior
* Public ID behavior
* Account deletion
* API contracts
* Important security rules

Do not create documentation for trivial implementation details that will quickly become stale.

Documentation should explain **why** important architectural decisions exist, not merely restate the code.

---

# 27. Existing PRD Has Priority

When the repository contains a PRD or implementation specification:

1. Read it.
2. Follow it.
3. Do not silently change product requirements.
4. If implementation conflicts with the PRD, identify the conflict.
5. Choose the least disruptive implementation.
6. Ask for clarification only when the conflict materially affects the result.

Do not invent product behavior that is not specified unless necessary to complete the technical implementation.

---

# 28. Change Management

Before modifying code, identify:

```text
Requirement
    ↓
Affected components
    ↓
Existing implementation
    ↓
Required changes
    ↓
Potential regressions
```

Keep changes scoped.

Do not refactor unrelated code merely because it could be cleaner.

Avoid:

> "While I'm here, I'll rewrite the entire repository."

Prefer:

> "Change only what is necessary to implement the requirement safely."

---

# 29. Code Quality Standard

Code should be:

* Readable
* Idiomatic
* Testable
* Secure
* Efficient
* Maintainable
* Consistent with the project

Prefer clear code over clever code.

Bad:

```kotlin
val x = users.filter { it.a && !it.b }.map { it.c }.firstOrNull()
```

when the intent is unclear.

Better:

```kotlin
val availableUser = users
    .firstOrNull { user ->
        user.isAvailable && !user.isBlocked
    }
```

Clarity wins.

---

# 30. Agent Workflow

For every non-trivial task, follow this workflow:

```text
1. Read AGENTS.md
        ↓
2. Inspect repository
        ↓
3. Find PRD/specification
        ↓
4. Inspect existing implementation
        ↓
5. Check installed skills
        ↓
6. Read relevant skills
        ↓
7. Identify required libraries/APIs
        ↓
8. Verify APIs with Context7
        ↓
9. Design the smallest correct change
        ↓
10. Implement
        ↓
11. Test/build
        ↓
12. Inspect for regressions
        ↓
13. Review security implications
        ↓
14. Update documentation/migrations if required
        ↓
15. Report exactly what changed
```

Do not skip steps simply because the task appears easy when the change touches architecture, backend, identity, security, or dependencies.

---

# 31. When Context7 Is Unavailable

If Context7 cannot provide the required documentation:

1. Inspect the project's existing usage.
2. Check the dependency version.
3. Use official documentation if available.
4. Avoid relying on potentially outdated memory.
5. Clearly mention uncertainty if the API cannot be verified.

Never fabricate an API.

---

# 32. When a Skill Is Unavailable

If no installed skill covers the task:

* Follow this `AGENTS.md`.
* Follow the project's existing architecture.
* Use Context7 where applicable.
* Prefer official documentation.
* Keep implementation conservative and minimal.

Do not invent project-specific conventions.

---

# 33. Agent Efficiency Rules

The agent should work efficiently.

Avoid:

* Re-reading the entire repository repeatedly.
* Searching for information already available.
* Making unnecessary tool calls.
* Rewriting unchanged files.
* Running expensive commands repeatedly without reason.
* Introducing dependencies for trivial problems.
* Performing broad refactors for localized tasks.

Prefer:

* Targeted repository searches.
* Existing abstractions.
* Incremental validation.
* Focused builds/tests.
* Reusing retrieved documentation.
* Small, reviewable changes.

Efficiency means reducing unnecessary work, **not skipping validation**.

---

# 34. Final Response Format

After completing an implementation, report:

### Changed

List the important files/components modified.

### Implemented

Summarize the actual behavior added or changed.

### Backend

Mention relevant:

* migrations
* RLS
* database functions
* constraints
* indexes

### Validation

Mention:

* build result
* tests
* relevant checks

### Notes

Mention any remaining limitation, uncertainty, or manual step.

Do not claim a test/build passed unless it was actually run.

---

# 35. Non-Negotiable Rules

The following rules always apply:

1. **Read `AGENTS.md` before working.**
2. **Inspect the existing project before creating new architecture.**
3. **Check installed skills and use relevant ones.**
4. **Use Context7 for current library/API documentation.**
5. **Never guess APIs when they can be verified.**
6. **Prefer the smallest correct implementation.**
7. **Do not unnecessarily add dependencies.**
8. **Keep security-sensitive rules server-side.**
9. **Treat Supabase RLS and database constraints as part of application security.**
10. **Maintain a single source of truth for user identity.**
11. **Do not create competing client/server public IDs.**
12. **Protect anonymous-user privacy.**
13. **Do not expose secrets or sensitive backend information.**
14. **Do not make unrelated refactors.**
15. **Run appropriate validation before declaring completion.**
16. **Never claim something was tested if it was not tested.**
17. **Follow the PRD and existing project specifications.**
18. **Prefer maintainability and clarity over cleverness.**

---

# 36. Guiding Principle

When uncertain, choose the solution that is:

> **Correct → Secure → Simple → Efficient → Consistent with the existing project.**

The agent's job is not merely to make the code compile.

The goal is to produce code that can realistically be maintained and shipped.
