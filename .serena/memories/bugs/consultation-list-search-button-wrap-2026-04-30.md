# Consultation list search button wrapped to next row

Date: 2026-04-30

## Symptom
On the consultation list page, the top search button wrapped down to a second line while the other filters stayed on the first row.

## Root cause
The `.search-bar` CSS grid defined 9 columns, but the form renders 10 controls: date range, start date, separator, end date, status, path type, search type, keyword, checkbox, and search button. The final button was auto-placed on the next grid row.

## Fix
Updated `src/main/resources/static/assets/css/components.css` and mirrored `src/main/resources/static/assets2/css/components.css` so `.search-bar` defines 10 columns with narrower minimum widths. Added a `.search-bar .btn` rule to keep the submit button centered and non-wrapping inside its grid cell.

## Verification
`./gradlew test` passed.