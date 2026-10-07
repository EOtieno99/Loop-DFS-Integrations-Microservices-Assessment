#!/usr/bin/env bash
set -euo pipefail
API_URL="${API_URL:-http://localhost:8080}"
curl --fail-with-body -sS "${API_URL}/api/countries?page=0&size=20"
curl --fail-with-body -sS -X POST "${API_URL}/api/countries" -H 'Content-Type: application/json' -d '{"name":"Tanzania"}'
