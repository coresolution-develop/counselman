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

<!-- OMA:START — managed by oh-my-agent. Do not edit this block manually. -->

# oh-my-agent

## Architecture

- **SSOT**: `.agents/` directory (do not modify directly)
- **Response language**: Follows `language` in `.agents/oma-config.yaml`
- **Skills**: `.agents/skills/` (domain specialists)
- **Workflows**: `.agents/workflows/` (multi-step orchestration)
- **Subagents**: Same-vendor native dispatch via Codex custom agents in `.codex/agents/{name}.toml`; cross-vendor fallback via `oma agent:spawn`

## Per-Agent Dispatch

1. Resolve `target_vendor_for_agent` from `.agents/oma-config.yaml`.
2. If `target_vendor_for_agent === current_runtime_vendor`, use the runtime's native subagent path.
3. If vendors differ, or native subagents are unavailable, use `oma agent:spawn` for that agent only.

## Workflows

Execute by naming the workflow in your prompt. Keywords are auto-detected via hooks.

| Workflow | File | Description |
|----------|------|-------------|
| orchestrate | `orchestrate.md` | Parallel subagents + Review Loop |
| work | `work.md` | Step-by-step with remediation loop |
| ultrawork | `ultrawork.md` | 5-Phase Gate Loop (11 reviews) |
| plan | `plan.md` | PM task breakdown |
| brainstorm | `brainstorm.md` | Design-first ideation |
| review | `review.md` | QA audit |
| debug | `debug.md` | Root cause + minimal fix |
| scm | `scm.md` | SCM + Git operations + Conventional Commits |

To execute: read and follow `.agents/workflows/{name}.md` step by step.

## Auto-Detection

Hooks: `UserPromptSubmit` (keyword detection), `PreToolUse`, `Stop` (persistent mode)
Keywords defined in `.agents/hooks/core/triggers.json` (multi-language).
Persistent workflows (orchestrate, ultrawork, work) block termination until complete.
Deactivate: say "workflow done".

## Rules

1. **Do not modify `.agents/` files** — SSOT protection
2. Workflows execute via keyword detection or explicit naming — never self-initiated
3. Response language follows `.agents/oma-config.yaml`

## Project Rules

Read the relevant file from `.agents/rules/` when working on matching code.

| Rule | File | Scope |
|------|------|-------|
| backend | `.agents/rules/backend.md` | on request |
| commit | `.agents/rules/commit.md` | on request |
| database | `.agents/rules/database.md` | **/*.{sql,prisma} |
| debug | `.agents/rules/debug.md` | on request |
| design | `.agents/rules/design.md` | on request |
| dev-workflow | `.agents/rules/dev-workflow.md` | on request |
| frontend | `.agents/rules/frontend.md` | **/*.{tsx,jsx,css,scss} |
| i18n-guide | `.agents/rules/i18n-guide.md` | always |
| infrastructure | `.agents/rules/infrastructure.md` | **/*.{tf,tfvars,hcl} |
| mobile | `.agents/rules/mobile.md` | **/*.{dart,swift,kt} |
| quality | `.agents/rules/quality.md` | on request |

<!-- OMA:END -->
