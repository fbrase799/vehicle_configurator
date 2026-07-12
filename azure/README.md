# Azure deployment scripts

Deploys the Vehicle Configurator stack to **Azure Container Apps (ACA)**
using the public Docker images built by the `Publish Docker images`
GitHub Actions workflow. Authentication from GitHub to Azure uses
**OIDC federation** (no long-lived secrets).

## What gets created

| Azure resource | Name (default) | Purpose |
|---|---|---|
| Resource group | `rg-vehicle-configurator` | Container for everything else |
| Log Analytics workspace | `vc-logs` | Required by ACA for container logs |
| Container Apps environment | `vc-env` | Network + DNS boundary for the two apps |
| Container app | `backend` | Spring Boot + embedded SQLite, internal HTTP on :8080, **minReplicas=1** |
| Container app | `frontend` | nginx serving the Vue build, **external HTTPS** on :80, **minReplicas=1** |

Both apps run with `minReplicas=1` so the public demo URL is always
instantly reachable — no cold start when a recruiter opens the link.

SQLite lives inside the backend container (`jdbc:sqlite:/data/configurator.db`).
Orders and configurations are demo data only; replica recycling re-seeds
the catalog on the next start.

## One-time setup

Prerequisites:

- `az login` to the personal subscription you want to use (confirm with `az account show`).
- `gh auth login` (the `00-bootstrap-oidc.sh` script writes the three GitHub secrets via the `gh` CLI).
- Permission to create AAD app registrations in your tenant (standard on a pay-as-you-go subscription).

Steps:

```bash
./azure/00-bootstrap-oidc.sh     # creates AAD app, grants Contributor on the RG,
                                 # writes AZURE_CLIENT_ID / AZURE_TENANT_ID /
                                 # AZURE_SUBSCRIPTION_ID to GitHub repo secrets.
./azure/01-setup.sh              # creates the RG, Log Analytics, ACA env, 2 apps.
                                 # Prints the public URL at the end.
```

From this point on, every push to `main`:

1. Triggers `Publish Docker images` — builds backend and frontend images and pushes them to GHCR.
2. On success, triggers `Deploy to Azure` — runs `02-update-images.sh`, which rolls each container app forward to `:latest`.

`01-setup.sh` also removes a legacy `database` container app if one exists
(from the pre-SQLite MySQL deployment).

## Day-to-day commands

```bash
./azure/02-update-images.sh      # force a redeploy from local shell
./azure/03-teardown.sh           # cost-stop: delete the RG, KEEP OIDC bootstrap
./azure/04-delete.sh             # full nuke: delete the RG AND undo 00-bootstrap-oidc.sh
```

`03-teardown.sh` is the everyday "stop billing after a demo" command.
It deletes the resource group (returns immediately, `--no-wait`) and
intentionally leaves the AAD app, federated credential and GitHub
secrets in place so the next `01-setup.sh` works without re-running
`00-bootstrap-oidc.sh`.

`04-delete.sh` is the full reset. It deletes the resource group and
**blocks** until it is fully gone (the ACA managed environment delete
takes 10–30 min asynchronously), then deletes the AAD app — which
cascades to its service principal and federated credential — and finally
removes `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`
from the GitHub repo. After this you have to re-run
`00-bootstrap-oidc.sh` before `01-setup.sh` will work again.

You can also trigger the deploy workflow manually from the GitHub Actions tab ("Deploy to Azure" → **Run workflow**), which is useful after a teardown + setup when you want to redeploy without pushing a commit.

## Overriding defaults

All tunables live in [`config.env`](./config.env). Override per-run by
exporting variables first, e.g. to deploy to Frankfurt instead:

```bash
AZ_LOCATION=germanywestcentral ./azure/01-setup.sh
```

## Cost

- `frontend` and `backend` run with `minReplicas=1` (always warm for instant recruiter access).
- Combined: 0.75 vCPU + 1.5 GiB memory running 24/7 costs roughly **€12–18 / month** in West Europe.
- Log Analytics has a 5 GB / month free grant that this stack does not come close to consuming.

To spend ~zero: run `01-setup.sh` right before a demo, `03-teardown.sh` right after. Each takes 3–5 minutes.

## Debugging

```bash
az containerapp logs show -g rg-vehicle-configurator -n backend  --tail 100 --follow
az containerapp logs show -g rg-vehicle-configurator -n frontend --tail 100 --follow

az containerapp revision list -g rg-vehicle-configurator -n backend -o table
```

## What this setup does NOT do

- No custom domain / HTTPS certificate (uses the default `*.azurecontainerapps.io` FQDN).
- No persistent orders/configurations — replica recycling wipes demo data and re-seeds the catalog.
- No network isolation — the environment uses the public profile. For enterprise deployments use `--internal-only` and VNet integration.
