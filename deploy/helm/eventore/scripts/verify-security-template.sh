#!/usr/bin/env bash
# Acceptance tests for FEAT-1.2 / FEAT-1.3 Helm security wiring.
set -euo pipefail

CHART_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE="eventore-test"
FAILED=0

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILED=1; }

require_match() {
  local label="$1"
  local pattern="$2"
  local output="$3"
  if echo "$output" | grep -qE "$pattern"; then
    pass "$label"
  else
    fail "$label (expected pattern: $pattern)"
  fi
}

require_no_match() {
  local label="$1"
  local pattern="$2"
  local output="$3"
  if echo "$output" | grep -qE "$pattern"; then
    fail "$label (unexpected pattern: $pattern)"
  else
    pass "$label"
  fi
}

if ! command -v helm >/dev/null 2>&1; then
  echo "BLOCKED: helm CLI not installed"
  exit 2
fi

# FEAT-1.2 AC-1: values.yaml security block keys
VALUES="$CHART_DIR/values.yaml"
for key in apiToken apiTokenExistingSecret apiTokenSecretKey allowedOrigins; do
  if grep -q "${key}:" "$VALUES"; then
    pass "FEAT-1.2 AC-1: values.yaml has eventore.security.${key}"
  else
    fail "FEAT-1.2 AC-1: values.yaml missing eventore.security.${key}"
  fi
done

# FEAT-1.2 AC-4: overlay value files document security block
for overlay in values-admin.yaml values-readonly.yaml; do
  if grep -q "apiTokenExistingSecret" "$CHART_DIR/$overlay"; then
    pass "FEAT-1.2 AC-4: $overlay documents security overlay"
  else
    fail "FEAT-1.2 AC-4: $overlay missing security comments"
  fi
done

# FEAT-1.2 AC-5: unsecured default — no secret env, allowed-origins only in JSON
DEFAULT_OUT="$(helm template "$RELEASE" "$CHART_DIR" 2>/dev/null)"
require_no_match "FEAT-1.2 AC-6 compat: no EVENTORE_SECURITY_API_TOKEN when token unset" \
  "EVENTORE_SECURITY_API_TOKEN" "$DEFAULT_OUT"
require_match "FEAT-1.2 AC-6 compat: security allowed-origins present in SPRING_APPLICATION_JSON" \
  '"allowed-origins"' "$DEFAULT_OUT"

# FEAT-1.2 AC-2/3/7: token enabled — secret ref, not plaintext in ConfigMap JSON
SECURED_OUT="$(helm template "$RELEASE" "$CHART_DIR" \
  -f "$CHART_DIR/values-admin.yaml" \
  --set eventore.security.apiToken=test-token 2>/dev/null)"

require_match "FEAT-1.2 AC-7: EVENTORE_SECURITY_API_TOKEN in backend deployment" \
  "EVENTORE_SECURITY_API_TOKEN" "$SECURED_OUT"
require_match "FEAT-1.2 AC-2: allowed-origins in SPRING_APPLICATION_JSON security block" \
  '"allowed-origins"' "$SECURED_OUT"
require_match "FEAT-1.2 AC-3: api-auth Secret created for inline token" \
  "kind: Secret" "$SECURED_OUT"
require_match "FEAT-1.2 AC-3: token stored in Secret stringData" \
  'test-token' "$SECURED_OUT"
require_no_match "FEAT-1.2 AC-3: api-token not in ConfigMap SPRING_APPLICATION_JSON" \
  '"api-token": "test-token"' "$SECURED_OUT"

# FEAT-1.2 AC-5: NOTES template documents token setup
NOTES_TEMPLATE="$(cat "$CHART_DIR/templates/NOTES.txt")"
require_match "FEAT-1.2 AC-5: NOTES mentions Settings API token" \
  "Settings" "$NOTES_TEMPLATE"
require_match "FEAT-1.2 AC-5: NOTES mentions 401 verification" \
  "401" "$NOTES_TEMPLATE"

# FEAT-1.2 AC-2: token via env (not plaintext in ConfigMap JSON)
if echo "$SECURED_OUT" | grep -q '"api-token": "test-token"'; then
  fail "FEAT-1.2 AC-3: api-token must not be inlined in ConfigMap SPRING_APPLICATION_JSON"
elif echo "$SECURED_OUT" | grep -q 'EVENTORE_SECURITY_API_TOKEN'; then
  pass "FEAT-1.2 AC-2: api-token wired via EVENTORE_SECURITY_API_TOKEN env + allowed-origins in JSON"
else
  fail "FEAT-1.2 AC-2: missing EVENTORE_SECURITY_API_TOKEN env when token configured"
fi

# FEAT-1.3 AC-8: frontend inline token only when set
FRONTEND_OUT="$(helm template "$RELEASE" "$CHART_DIR" \
  --set frontend.env.apiToken=front-token 2>/dev/null)"
require_match "FEAT-1.3 AC-8: frontend-config.js includes apiToken when set" \
  'apiToken: "front-token"' "$FRONTEND_OUT"

FRONTEND_DEFAULT="$(helm template "$RELEASE" "$CHART_DIR" 2>/dev/null)"
require_no_match "FEAT-1.3 AC-8: frontend-config.js omits apiToken when unset" \
  'apiToken:' "$FRONTEND_DEFAULT"

FRONTEND_SECRET_OUT="$(helm template "$RELEASE" "$CHART_DIR" \
  --set frontend.env.apiTokenExistingSecret=eventore-frontend-token 2>/dev/null)"
if echo "$FRONTEND_SECRET_OUT" | grep -q 'inject-frontend-api-token' \
  && echo "$FRONTEND_SECRET_OUT" | grep -q 'eventore-frontend-token' \
  && echo "$FRONTEND_SECRET_OUT" | grep -q 'apiToken'; then
  pass "FEAT-1.3 AC-8: frontend apiTokenExistingSecret renders via initContainer Secret reference"
else
  fail "FEAT-1.3 AC-8: frontend.env.apiTokenExistingSecret not wired in chart templates"
fi

if [ "$FAILED" -ne 0 ]; then
  echo "Helm security template verification: FAILED"
  exit 1
fi

echo "Helm security template verification: ALL PASS"
exit 0
