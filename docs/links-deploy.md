# Link Hub (`links`) — Deploy Runbook

`links` is the company link hub (public links + member personalization: login/
signup, favorites, custom links, history, remember-me) packaged as a **standalone
Spring Boot app, deployable independently from CounselMan (csm)**.

- Port: **8085** (dev) / **18085** recommended on prod (matches the 1808x scheme).
- Shares the **same `csm` MySQL DB** as csm — `hub_member*` and `company_link*`
  tables are auto-created by the services (no DDL/migration needed). Members and
  links are therefore shared with csm's in-app hub.
- Coexists with csm: `/csm/links` (csm) stays untouched; the standalone app is
  additive. Build glue mirrors the other sibling apps (`mediplat`, `sms`,
  `cancer-treatment`).

## Build

| Task | Output |
|------|--------|
| `./gradlew linksDevJar` | `links/build/libs/links-dev.jar` |
| `./gradlew linksProdJar` | `links/build/libs/links-prod.jar` |
| `./gradlew packageDevDeploy` / `packageProdDeploy` | bundles the links jar with csm/mediplat/sms under `build/deploy/{dev,prod}/` |

## DEV deploy (CI on push to `dev`; apps reached by direct port)

### One-time server setup (root)
```bash
sudo mkdir -p /opt/links/app

# systemd unit — IMPORTANT: ExecStart java path must match this host's JDK 17,
# i.e. the same path mediplat uses. Check it:
#   sudo systemctl cat mediplat | grep ExecStart   → /usr/lib/jvm/java-17/bin/java
sudo cp scripts/systemd/links.service /etc/systemd/system/   # template already uses /usr/lib/jvm/java-17/bin/java
sudo systemctl daemon-reload && sudo systemctl enable links

# env: only the DB password is required (host/user/db default to the dev DB).
sudo tee /etc/default/links >/dev/null <<'EOF'
SPRING_DATASOURCE_PASSWORD=<dev DB password>
LINKS_PORT=8085
HUB_REMEMBER_COOKIE_SECURE=false   # dev is http; Secure cookies only ride https
# HUB_SIGNUP_CODE=core             # optional; defaults to "core"
EOF

# firewall: open 8085 like 8081-8083 (else http://<host>:8085 is unreachable)
sudo firewall-cmd --permanent --add-port=8085/tcp && sudo firewall-cmd --reload
```
> Where to get the DB password: the same value dev `mediplat` uses
> (`sudo systemctl cat mediplat` → its `EnvironmentFile`), i.e. the dev DB that
> `scripts/local-up.sh` connects to.

### Deploy
Push to `dev` → `.github/workflows/deploy-dev.yml` builds `links-dev.jar`, copies
it to `/opt/links/app/links.jar`, and runs `systemctl restart mediplat links`.
The unit + java path + env must exist first, or the restart fails.

### Access
`http://<dev-host>:8085/links` (port-direct, no reverse proxy on dev).
csm stays at `http://<dev-host>:8081/csm/links`.

## PROD deploy (nightly staging; behind httpd reverse proxy)

### One-time server setup (root)
```bash
sudo mkdir -p /opt/links/app
sudo cp scripts/systemd/links.service /etc/systemd/system/   # verify ExecStart java path
sudo tee /etc/default/links >/dev/null <<'EOF'
# Copy DB lines from /opt/csm-next/env/csm-next.env (csm/mediplat use the same DB):
#   sudo grep SPRING_DATASOURCE /opt/csm-next/env/csm-next.env
SPRING_DATASOURCE_URL=jdbc:mysql://<prod-db-host>:3306/csm?serverTimezone=Asia/Seoul&useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<pass>
LINKS_PORT=18085
HUB_REMEMBER_COOKIE_SECURE=true
HUB_SIGNUP_CODE=<code>
EOF
sudo systemctl daemon-reload && sudo systemctl enable links

# nightly-deploy unit ReadWritePaths now includes /opt/links/app — re-apply:
sudo cp scripts/deploy-nightly.sh /opt/csm-next/deploy/scripts/   # (server path per docs/deploy-automation.md)
sudo cp scripts/systemd/nightly-deploy.service /etc/systemd/system/ && sudo systemctl daemon-reload
```

### Deploy (nightly — same path as cancer-treatment/sms, NOT deploy-prod.sh)
```bash
scp links/build/libs/links-prod.jar PROD:/opt/.../deploy/staging/links.jar
ssh PROD 'touch /opt/.../deploy/staging/deploy.ok'   # applied at the next nightly window
```

### Reverse proxy (httpd) — keep csm, add links
Leave `/csm/* → 18081` (csm) untouched. Add a route to links `18085`. A
**subdomain** is cleanest (avoids colliding with `/` → mediplat, which already
redirects `/links` to csm):
```apache
# /etc/httpd/conf.d/links-route.conf
<VirtualHost *:443>
    ServerName links.sosyge.net
    ProxyPreserveHost On
    RequestHeader set X-Forwarded-Proto "https"
    ProxyPass        /  http://127.0.0.1:18085/  retry=0 timeout=3600 disablereuse=on
    ProxyPassReverse /  http://127.0.0.1:18085/
</VirtualHost>
```

## Notes
- **DB:** no schema work — `hub_member`, `hub_member_favorite`,
  `hub_member_custom_link`, `hub_member_link_history`, `hub_member_token`,
  `company_link`, `company_link_category` are created idempotently on first use.
- **Sessions:** in-memory (single instance). Persistent login survives restarts
  via the DB-backed remember-me token, so an in-memory session store is fine.
- **mediplat:** still redirects `/links` to csm's `/csm/links`; unchanged. Only
  repoint it at cutover when csm's in-app hub is retired.
- **Ports:** 8081 csm · 8082 mediplat · 8083 cancer-treatment · 8084 sms ·
  8085/18085 links — no collision.
