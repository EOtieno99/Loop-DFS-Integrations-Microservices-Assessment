# Deployment guide

## Container and prerequisites

Install Docker, kubectl and access to a Kubernetes cluster. Enable a default StorageClass for the demo MySQL PVC and Metrics Server for HPA. Inspect `kubectl config current-context` to confirm the intended cluster before changing it. Application images run as UID 10001; Java 17 is used in build/runtime stages.

```sh
./mvnw verify
docker build -t YOUR_REGISTRY/loop-assessment:VERSION .
docker push YOUR_REGISTRY/loop-assessment:VERSION
```

Use an immutable digest for production. Configure registry pull credentials if private. For kind/minikube, build `loop-assessment:local`, then load it with `kind load docker-image loop-assessment:local` or `minikube image load loop-assessment:local` instead of pushing. The script accepts `IMAGE=loop-assessment:local`.

## Secrets and database

`country-secrets` is intentionally not committed. Create it from a protected file populated from your secret manager:

```sh
kubectl apply -f k8s/namespace.yaml
# /secure/path/country.env contains DB_PASSWORD=... and MYSQL_ROOT_PASSWORD=...
# Restrict permissions; do not commit this file.
kubectl -n loop-assessment create secret generic country-secrets \
  --from-env-file=/secure/path/country.env
```

Production should use a managed MySQL 8.4 instance with backups, failover, least-privilege application credentials and TLS. Pass `DB_URL` to the script; use driver options such as `sslMode=VERIFY_IDENTITY` and provide the relevant CA trust configuration. Demo database deployment is a single persistent StatefulSet, not a highly available production database. MYSQL_ROOT_PASSWORD is required only for the demo. For external MySQL the Secret needs only DB_PASSWORD; create the database before deploying.

Flyway runs on startup and serializes concurrent migrations through its schema-history lock. Hibernate validates the resulting schema. For future large migrations, use a dedicated migration Job and an expand/contract rollout so old and new replicas can coexist. Review backups before applying schema changes. Use a privileged migration role and a restricted runtime role for production if separating migration execution.

## Deploy

From the project root:

```sh
# Local demonstration with the included MySQL StatefulSet:
IMAGE=loop-assessment:local DEMO_MYSQL=true ./scripts/deploy.sh
# Production with an already configured external MySQL:
IMAGE=YOUR_REGISTRY/loop-assessment@sha256:YOUR_DIGEST \
DB_URL='jdbc:mysql://YOUR_DB_HOST:3306/loop_assessment?sslMode=VERIFY_IDENTITY' \
DB_USERNAME=loop ./scripts/deploy.sh
```

The script applies the namespace, verifies the required Secret keys, generates config, optionally starts MySQL, renders the selected image into the deployment, applies HPA/PDB, restarts pods to load configuration, and waits for the rollout. It prints and uses your current kubectl context and does not build/push images or create credentials. Optional variables are DB_USERNAME (loop), DB_POOL_SIZE (10), and SOAP_ENDPOINT (the supplied public provider). `k8s/configmap.yaml` documents the demo defaults; the script generates its ConfigMap from these variables, so editing that file does not change scripted deployment. Deployment is rolling with zero unavailable replicas and one surge pod. Read-only root filesystem, dropped capabilities, non-root UID, resource budgets, ephemeral `/tmp`, probes and graceful termination are configured.

```sh
kubectl -n loop-assessment get pods,svc,hpa,pdb,pvc
kubectl -n loop-assessment port-forward svc/country-api 8080:80
# In another terminal:
API_URL=http://localhost:8080 ./scripts/smoke-test.sh
```

The smoke script imports Tanzania and therefore writes to the database. On a repeated run, its POST can return the expected 409 duplicate response. For a repeatable read-only check, use `curl --fail-with-body 'http://localhost:8080/api/countries?page=0&size=20'`. A successful GET verifies the HTTP/database path; a first successful POST additionally verifies SOAP access from the cluster. Expect two Ready API pods and ready Service endpoints:

```sh
kubectl -n loop-assessment get endpointslices -l kubernetes.io/service-name=country-api
```

The ClusterIP Service exposes only port 80 to the API's 8080. Management 8081 is private; port-forward an application pod to inspect health/metrics. Startup allows five minutes for database initialization/migration. Liveness checks application availability; readiness checks application + database. HPA requires resource metrics; at low traffic replicas remain at 2.

For public access, supply an ingress/API gateway with your domain, TLS certificate, authentication/authorization, body limits and rate limiting. An unauthenticated public ingress is deliberately not supplied. Optional `kubectl apply -f k8s/network-policy.yaml` restricts API ingress to `ingress-nginx` and same-namespace pods labeled `access-country-api=true`, management to namespace `monitoring`, and demo MySQL to the app. Adjust selectors to your infrastructure and ensure the CNI implements NetworkPolicy. Egress remains permitted for DNS, MySQL and the public SOAP provider.

## Updates, rollback and configuration

```sh
kubectl -n loop-assessment set image deployment/country-api app=YOUR_REGISTRY/loop-assessment:NEW_VERSION
kubectl -n loop-assessment rollout status deployment/country-api
# On application regression, provided schema remains backward compatible:
kubectl -n loop-assessment rollout undo deployment/country-api
```

Image rollback does not reverse migrations. ConfigMap/Secret changes require `kubectl -n loop-assessment rollout restart deployment/country-api`; verify rollout afterwards. MySQL initialization environment variables do not rotate passwords on an existing data volume: coordinate an actual MySQL password change with the application Secret.

The default six-replica maximum consumes up to 60 pool connections plus rolling surge and admin capacity. Tune pool size, HPA, bulkhead, CPU/memory and provider rate limits together after load testing. Back up and test restoring MySQL. Deleting the namespace is destructive and is not part of the deployment script.

Rollout commands follow the [Kubernetes Deployment reference](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/). Probe behavior is described in the [Kubernetes probe guide](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/).

## Publish to GitHub

After reviewing the code and confirming `.env` and credentials are ignored, use your chosen repository:

```sh
git init
git add .
git commit -m "Implement country SOAP integration, CRUD, tests and Kubernetes deployment"
git branch -M main
git remote add origin YOUR_GITHUB_REPOSITORY_URL
git push -u origin main
```

Share the repository URL after the push. GitHub account/repository selection and the assessment's submission deadline are external to this implementation.
