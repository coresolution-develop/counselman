# Company links column preference

Date: 2026-04-30

## Request
User wanted the `/links` page card layout, currently appearing as 2 cards per row, to be user-configurable and saved in localStorage.

## Root cause
The link card grid used fixed responsive CSS (`auto-fill/minmax`) with no user preference state, so users could not choose the number of cards per row.

## Fix
Updated `src/main/resources/templates/design/company-links.html`:
- Introduced `--lh-link-columns` CSS variable.
- Added a select control in the `바로가기` panel header for 1-4 cards per row.
- Persisted the selected value under localStorage key `companyLinks.columns`.
- Kept mobile layout at 1 column to avoid overflow.

## Verification
`./gradlew test` passed.