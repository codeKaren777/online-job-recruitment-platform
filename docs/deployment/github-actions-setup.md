# GitHub Actions setup

The workflow is `.github/workflows/deploy-production.yml`. The confirmed repository default/deployment branch is `main`. Pushes to `main` that affect application, Docker, or workflow inputs build and test the backend. Published GitHub releases also run the pipeline. Manual runs are allowed only from `main`; ordinary pull requests and feature branches never deploy.

## Repository secrets

In **Settings → Secrets and variables → Actions → Secrets**, add:

| Secret | Value |
| --- | --- |
| `LIGHTSAIL_HOST` | Lightsail public IP address or DNS hostname; supply the real server address. |
| `LIGHTSAIL_SSH_USER` | `ubuntu`. |
| `LIGHTSAIL_SSH_PRIVATE_KEY` | Private key whose public half is authorized for `ubuntu` on the instance. Use a dedicated deployment key. |
| `LIGHTSAIL_KNOWN_HOSTS` | Pinned SSH known-hosts line(s) for the exact host in `LIGHTSAIL_HOST`, including port if nonstandard. Obtain and verify the fingerprint through a trusted server/console channel before saving it. |

The workflow uses `StrictHostKeyChecking=yes`; it does not use `ssh-keyscan` at deploy time or disable host verification. Do not store private keys or tokens in repository files. No GHCR credentials are sent over SSH because the package is public and Lightsail pulls the image anonymously.

## Repository variables

In the Actions **Variables** tab, set:

| Variable | Value |
| --- | --- |
| `DEPLOY_PATH` | Optional; defaults to `/opt/recruitment`. Keep the requested path unless there is a deliberate server-side change. |
| `GHCR_IMAGE` | Optional full lowercase image name, such as `ghcr.io/<owner>/online-job-recruitment-platform-backend`. Defaults to the repository owner plus that image name. |

Configure the GHCR package as public. Publishing uses the workflow's `GITHUB_TOKEN`, with `contents: read` and `packages: write`; these permissions are set in the build and publish job. If organization policy restricts `GITHUB_TOKEN` package publishing, enable package write for Actions in the organization/repository settings. The Lightsail deploy job does not need package-read permissions or a deployment token.

## SSH key setup

On a trusted developer workstation, generate a dedicated key pair, for example:

```sh
ssh-keygen -t ed25519 -a 64 -f lightsail-recruitment-deploy -C github-actions-recruitment
```

Install the `.pub` key in `/home/ubuntu/.ssh/authorized_keys` using the existing trusted access path. Set private-key permissions appropriately on the workstation and copy the private key contents into `LIGHTSAIL_SSH_PRIVATE_KEY`. Verify the Lightsail host-key fingerprint independently, then save the matching known-hosts line as `LIGHTSAIL_KNOWN_HOSTS`. Do not assume an unverified `ssh-keyscan` result is trusted.

## GHCR package

The package `ghcr.io/codekaren777/online-job-recruitment-platform-backend` must remain public. Lightsail pulls `:<full-SHA tag>` anonymously with `docker pull`; no `GHCR_DEPLOY_TOKEN`, `GHCR_USERNAME`, `GHCR_DEPLOY_USER`, `docker login ghcr.io`, or server-side credential is required.

## Manual deployment and logs

After server setup is complete, open **Actions → Build and deploy production backend → Run workflow**, select `main`, and run it. A manual workflow run on any other branch is rejected by the job condition. The pipeline runs tests, packages the application, builds/publishes the image, copies the production Compose/support files over SSH, then deploys the full-SHA image tag and verifies the health endpoint.

The `build-and-publish` and `deploy` jobs show each step and failure output. A failed deployment step includes backend logs and the script attempts a rollback to the last healthy image tag. Workflow concurrency prevents two production deployments running at once. GitHub releases can also be published through the Releases interface; release tags and the full commit SHA are published.
