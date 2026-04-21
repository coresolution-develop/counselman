## Project profile
- This repository is a Spring Boot application.
- Prioritize stability, clear diffs, and reviewable changes over broad refactors.
- Preserve existing architecture unless the task explicitly asks for structural change.

## Working agreements
- Explain the root cause before proposing a fix when debugging.
- Prefer minimal, local changes over wide-ranging rewrites.
- Do not rename packages, modules, endpoints, or database columns unless explicitly requested.
- Do not add new dependencies unless clearly justified in the response.
- Keep comments concise and only where the code is non-obvious.
- Preserve existing coding style unless it is clearly broken.

## Source of truth
- Follow existing conventions in:
  - `src/main/java`
  - `src/main/resources/templates`
  - `src/main/resources/static`
  - `build.gradle`
- When multiple patterns exist, prefer the most recently used pattern near the edited file.

## Backend rules
- For controller changes, verify request mapping, validation, response DTO shape, and error handling.
- For service changes, check transaction boundaries, null handling, and rollback implications.
- For repository/query changes, check SQL/JPQL correctness and whether indexes or joins may affect performance.
- Avoid hidden behavior changes in authentication, authorization, or session handling.
- Do not silently change API contracts.

## Frontend rules
- Preserve current HTML structure and CSS class names unless the task explicitly includes markup changes.
- For Thymeleaf, keep server-side rendering assumptions intact.
- For JavaScript fixes, prefer patching the broken flow rather than rewriting the whole file.

## Configuration and operations
- Treat production configuration as sensitive.
- Do not modify nginx/httpd/tomcat/systemd/deployment scripts unless the task explicitly asks for infra changes.
- For application config changes, call out environment-specific impact (`dev`, `prod`, env vars, ports, secrets).
- Never invent secret values. Use placeholders and explain where they must be supplied.

## Database and migrations
- Do not drop or rename tables/columns without explicit instruction.
- For schema changes, propose migration-safe steps.
- For destructive SQL, stop and explain the risk first.

## Validation checklist
Before finishing, verify:
- The change is scoped to the request.
- No unrelated files were modified.
- The diff is easy to review.
- Any impacted tests or checks are identified.

## Done when
A task is complete only when:
1. The requested behavior is implemented or the root cause is clearly identified.
2. A reviewer can understand the change from the diff and summary.
3. Risks, assumptions, and follow-up checks are stated clearly.

## Review guidelines
Flag these aggressively:
- auth/authz regressions
- accidental API contract changes
- missing validation
- null/empty edge cases
- broken transaction handling
- environment-specific config mistakes
- logging of sensitive values