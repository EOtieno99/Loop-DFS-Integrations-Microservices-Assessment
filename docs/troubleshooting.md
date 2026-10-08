# Troubleshooting and Monitoring

Start with scope: one request, one pod, every replica, database, or the SOAP provider. Record the time, HTTP status and `X-Request-ID`; JSON application logs include that ID.

Useful commands:

```sh
kubectl -n loop-assessment get pods -o wide
kubectl -n loop-assessment get events --sort-by=.lastTimestamp
kubectl -n loop-assessment describe deployment country-api
kubectl -n loop-assessment describe pod POD_NAME
kubectl -n loop-assessment logs POD_NAME -c app --since=10m
kubectl -n loop-assessment logs POD_NAME -c app --previous
kubectl -n loop-assessment rollout status deployment/country-api
```

Replace POD_NAME with a name from `get pods`. 

Check Service routing when pods are healthy but HTTP requests fail:

```sh
kubectl -n loop-assessment get svc country-api -o yaml
kubectl -n loop-assessment get endpointslices -l kubernetes.io/service-name=country-api
kubectl -n loop-assessment get pods -l app=country-api --show-labels
```

Common Errors with Kubernetes and Remediation


| Symptom | Evidence and action |
| --- | --- |
| ImagePullBackOff | Check image name/digest, registry connectivity and imagePullSecrets. For local clusters load the image into each node. |
| Pending pods | Describe pod/PVC; check node CPU/memory, StorageClass/PV provisioning, scheduling events and quotas. |
| CrashLoopBackOff | Inspect previous logs for missing Secret/config, migration failure or OOM. Fix cause before rollout restart. |
| Flyway failure | Check schema-history errors, credentials and permissions; compare migration checksums with deployed source. Never edit an applied migration or blindly repair history. Add a new migration after review. |
| Readiness failing | Forward port 8081; inspect readiness status and DB connection errors; verify DB DNS, credentials, network policy and pool saturation. Provider failures alone should not fail readiness. |
| HTTP 400 | Correct JSON, nonblank name, field bounds, page/size, positive ID or unique language codes. |
| HTTP 404 | Country absent locally, unknown provider country or wrong route. POST imports; GET never calls SOAP. |
| HTTP 409 | Country already imported, ISO/name collision or stale version. GET current version before PUT. |
| HTTP 502 | SOAP fault, malformed/wrong namespace XML, missing fields or inconsistent provider data. Use SoapUI/WSDL and inspect bounded operation logs. |
| HTTP 503 | Provider timeout/retry exhaustion/open circuit/bulkhead full, or DB unavailable. Check error code, circuit state, retries, permits, database logs and capacity. Respect Retry-After. |
| 200 with X-Data-Stale=true | Explicit saved-data fallback during provider failure. Inspect timestamps and provider metrics. |
| OOMKilled | Check last termination reason and memory graphs. Review JVM budget, payload sizes, thread counts and pod limits before increasing limits. |
| HPA unknown metrics | Check Metrics Server, `kubectl top pods`, HPA events and CPU requests. |
| Rollout stuck | Check readiness, image, quota/resources, DB migration and PDB; keep the working deployment until diagnosis. |

## Health and metrics checks

```sh
kubectl -n loop-assessment port-forward deployment/country-api 8081:8081
curl -i http://localhost:8081/actuator/health/liveness
curl -i http://localhost:8081/actuator/health/readiness
curl http://localhost:8081/actuator/prometheus
kubectl -n loop-assessment top pods
kubectl -n loop-assessment describe hpa country-api
```

## Connectivity and database

Use a temporary diagnostic pod with an appropriate image (not an application container containing a shell toolkit).

Resolve `mysql`, confirm TCP 3306, and check the configured SOAP host on port 80/443. 

Label same-namespace diagnostics `access-country-api=true` when optional policies are active.

For the demo inspect `kubectl -n loop-assessment logs mysql-0` and PVC status. Run read-only checks with existing secured credentials: `SELECT 1`, `SHOW TABLES`, schema history and country/language counts.

Actuator exposure and health configuration. The application exposes health, info and Prometheus only on its management port.

## Reproduce SOAP client failures locally

```sh
./mvnw -Dtest=SoapCountryClientTest,SoapXmlTest test
```

`src/test/java/com/example/loop_assessment/soap_resources/SoapCountryClientTest.java` starts a local HTTP mock provider to check the two SOAP calls, transient retries, timeouts, and circuit behavior. These tests do not call the public provider or use Kubernetes. If they pass while cluster POST requests fail, check the deployed SOAP_ENDPOINT, cluster DNS/egress, provider availability, and pod logs. Use a request ID to correlate a controlled request with logs:

```sh
curl -i -X POST http://localhost:8080/api/countries \
  -H 'Content-Type: application/json' -H 'X-Request-ID: k8s-soap-check' \
  -d '{"name":"Kenya"}'
kubectl -n loop-assessment logs -l app=country-api -c app --since=5m --prefix=true
```

