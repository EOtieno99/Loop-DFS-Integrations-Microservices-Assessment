#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v kubectl >/dev/null || { echo 'kubectl is required' >&2; exit 1; }
: "${IMAGE:?Set IMAGE to a built and pushed image tag or digest}"
if [[ ! "$IMAGE" =~ ^[a-zA-Z0-9./:@_-]+$ ]]; then
  echo 'IMAGE contains invalid characters' >&2
  exit 1
fi
case "${DEMO_MYSQL:-false}" in
  true|false) ;;
  *) echo 'DEMO_MYSQL must be true or false' >&2; exit 1 ;;
esac
echo "Deploying to context: $(kubectl config current-context)"
kubectl apply -f k8s/namespace.yaml
if ! kubectl -n loop-assessment get secret country-secrets >/dev/null 2>&1; then
  echo 'Create country-secrets first; see docs/deployment.md' >&2
  exit 1
fi
# Check required keys without printing credentials.
for key in DB_PASSWORD; do
  if [[ -z "$(kubectl -n loop-assessment get secret country-secrets -o "jsonpath={.data.${key}}")" ]]; then
    echo "country-secrets is missing a nonempty ${key}" >&2
    exit 1
  fi
done
if [[ "${DEMO_MYSQL:-false}" == true ]]; then
  if [[ -z "$(kubectl -n loop-assessment get secret country-secrets -o 'jsonpath={.data.MYSQL_ROOT_PASSWORD}')" ]]; then
    echo 'Demo MySQL requires MYSQL_ROOT_PASSWORD in country-secrets' >&2
    exit 1
  fi
else
  : "${DB_URL:?Set DB_URL to the external MySQL JDBC URL, or set DEMO_MYSQL=true}"
fi
rendered_config=$(mktemp)
trap 'rm -f "$rendered_config"' EXIT

kubectl -n loop-assessment create configmap country-config \
  --from-literal="DB_URL=${DB_URL:-jdbc:mysql://mysql:3306/loop_assessment}" \
  --from-literal="DB_USERNAME=${DB_USERNAME:-loop}" \
  --from-literal="DB_POOL_SIZE=${DB_POOL_SIZE:-10}" \
  --from-literal="SOAP_ENDPOINT=${SOAP_ENDPOINT:-http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso}" \
  --dry-run=client -o yaml > "$rendered_config"
kubectl apply -f "$rendered_config"
if [[ "${DEMO_MYSQL:-false}" == true ]]; then
  kubectl apply -f k8s/mysql-demo.yaml
  kubectl -n loop-assessment rollout status statefulset/mysql --timeout=300s
fi

# Render image before applying so the placeholder image never starts.
sed "s|image: loop-assessment:local|image: ${IMAGE}|" k8s/app.yaml | kubectl apply -f -
kubectl apply -f k8s/scaling.yaml
# Environment variables from ConfigMaps/Secrets are read only at pod startup.
# Restart also picks up configuration changes when IMAGE is unchanged.
kubectl -n loop-assessment rollout restart deployment/country-api
kubectl -n loop-assessment rollout status deployment/country-api --timeout=300s
kubectl -n loop-assessment get pods,svc,hpa,pdb,pvc
