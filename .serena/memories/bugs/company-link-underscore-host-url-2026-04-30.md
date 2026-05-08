# Company link URL with underscore host rejected

Date: 2026-04-30

## Symptom
Adding `https://stay_djm.cspay.co.kr/products` on `/links` failed with `URL에 도메인이 필요합니다.`.

## Root cause
`CompanyLinkService.normalizeUrl` used `URI.getHost()` to decide whether the URL had a domain. Java URI parsing returns `null` for hosts containing underscores, even when the URL has a valid authority and browsers can open it.

## Fix
`CompanyLinkService` now accepts URLs when `URI.getHost()` is present or, for strict-parser edge cases, when `URI.getRawAuthority()` contains a nonblank host segment. Scheme validation still only allows `http` and `https`; malformed URLs without authority such as `http:/example.com` are still rejected.

## Regression test
`CompanyLinkServiceTest.createLink_acceptsHostWithUnderscore` covers `https://stay_djm.cspay.co.kr/products`.

## Verification
`./gradlew test` passed.