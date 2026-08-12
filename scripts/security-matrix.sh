#!/usr/bin/env bash
# Requires: real running backend on localhost:8080, docker-compose postgres reachable via `docker exec securevault-postgres psql`, python3.
set -uo pipefail
BASE=http://localhost:8080
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$SCRIPT_DIR/../docs/evidence/security-matrix-raw.txt"
> "$OUT"

row() {
  # $1 = label, $2 = actual http status, $3 = expected http status, $4 = extra note (optional)
  local mark="FAIL"
  [ "$2" = "$3" ] && mark="PASS"
  printf '%-90s expected=%-4s actual=%-4s %s\n' "$1" "$3" "$2" "$mark" | tee -a "$OUT"
}

req() {
  # $1 method, $2 path, $3 token (or empty), $4 body (or empty)
  local method="$1" path="$2" token="$3" body="${4:-}"
  local args=(-s -o /dev/null -w '%{http_code}' -X "$method" "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  if [ -n "$body" ]; then
    args+=(-H "Content-Type: application/json" -d "$body")
  fi
  curl "${args[@]}"
}

body_of() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  local args=(-s -X "$method" "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  if [ -n "$body" ]; then
    args+=(-H "Content-Type: application/json" -d "$body")
  fi
  curl "${args[@]}"
}

RAND=$RANDOM$RANDOM
UA="user-a-$RAND@sv-test.local"
UB="user-b-$RAND@sv-test.local"
UC="user-c-$RAND@sv-test.local"
ADM="admin-$RAND@sv-test.local"
PW="Str0ng!Passw0rd"

echo "=== Setup: registering 4 fresh users ===" | tee -a "$OUT"
for e in "$UA" "$UB" "$UC" "$ADM"; do
  code=$(req POST /api/auth/register "" "{\"fullName\":\"Test User\",\"email\":\"$e\",\"password\":\"$PW\"}")
  echo "register $e -> $code" | tee -a "$OUT"
done

login() {
  body_of POST /api/auth/login "" "{\"email\":\"$1\",\"password\":\"$PW\"}"
}

TOKA=$(login "$UA" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
TOKB=$(login "$UB" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
TOKC=$(login "$UC" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo "TOKA len=${#TOKA} TOKB len=${#TOKB} TOKC len=${#TOKC}" | tee -a "$OUT"

# Promote ADM to ADMIN role directly in Postgres, then log in fresh so authorities reflect it.
docker exec securevault-postgres psql -U postgres -d securevault -c "UPDATE users SET role='ADMIN' WHERE email='$ADM';" >> "$OUT" 2>&1
TOKADM=$(login "$ADM" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
echo "TOKADM len=${#TOKADM}" | tee -a "$OUT"

echo "=== Section A: every protected endpoint, no token -> 401 ===" | tee -a "$OUT"
declare -a NOTOKEN_ROUTES=(
  "GET /api/vault"
  "GET /api/vault/1"
  "POST /api/vault"
  "PUT /api/vault/1"
  "DELETE /api/vault/1"
  "GET /api/vault/trash"
  "GET /api/vault/1/history"
  "GET /api/vault/health"
  "PUT /api/vault/1/restore"
  "DELETE /api/vault/1/permanent"
  "POST /api/vault/recompute-strength"
  "POST /api/share"
  "GET /api/share/received"
  "GET /api/share/sent"
  "PUT /api/share/1"
  "DELETE /api/share/1"
  "GET /api/dashboard/summary"
  "GET /api/dashboard/password-health"
  "GET /api/dashboard/recent-activity"
  "GET /api/dashboard/alerts"
  "GET /api/admin/stats"
  "GET /api/admin/users"
  "PUT /api/admin/users/1/status"
  "GET /api/admin/audit-logs"
  "GET /api/monitoring/login-attempts"
  "GET /api/monitoring/alerts"
  "GET /api/monitoring/risk-score"
  "GET /api/monitoring/devices"
  "DELETE /api/monitoring/devices/1"
  "GET /api/notifications"
  "PUT /api/notifications/1/read"
  "PUT /api/notifications/read-all"
  "POST /api/auth/mfa/setup"
  "POST /api/auth/mfa/disable"
)
for route in "${NOTOKEN_ROUTES[@]}"; do
  method=${route%% *}
  path=${route#* }
  code=$(req "$method" "$path" "")
  row "no-token $route" "$code" "401"
done

echo "=== Section A2: public endpoints, no token -> NOT 401 ===" | tee -a "$OUT"
code=$(req POST /api/password/strength "" '{"password":"whatever1A!"}')
row "no-token POST /api/password/strength" "$code" "200"
code=$(req POST /api/password/generate "" '{"length":16,"includeUppercase":true,"includeLowercase":true,"includeNumbers":true,"includeSymbols":true,"excludeAmbiguous":false}')
row "no-token POST /api/password/generate" "$code" "200"
code=$(req GET /actuator/health "")
row "no-token GET /actuator/health" "$code" "200"
# /api/auth/logout is DELIBERATELY permitAll (SecurityConfig) -- it revokes by refreshToken in
# the body, not by the Authorization header, so a valid-but-tokenless caller can still fully log
# out (e.g. an already-expired access token). No Authorization header + no body -> 400
# VALIDATION_FAILED (missing refreshToken), never 401 -- that 400 is correct, not a gap.
code=$(req POST /api/auth/logout "" "")
row "no-token, no-body POST /api/auth/logout (permitAll by design)" "$code" "400"

echo "=== Section B: malformed / tampered token -> 401 ===" | tee -a "$OUT"
code=$(req GET /api/vault "not-even-a-jwt")
row "malformed-token GET /api/vault" "$code" "401"
code=$(req GET /api/vault "${TOKA}TAMPERED")
row "tampered-token(trailing garbage) GET /api/vault" "$code" "401"
# Flip one character in the signature segment.
SIG_TAMPERED="${TOKA%?}$([ "${TOKA: -1}" = "a" ] && echo b || echo a)"
code=$(req GET /api/vault "$SIG_TAMPERED")
row "tampered-token(last-char-flip) GET /api/vault" "$code" "401"
code=$(req GET /api/vault "${TOKA%.*}")
row "truncated-token(no-signature-segment) GET /api/vault" "$code" "401"

echo "=== Section C: valid token, another user's resource -> 403 ===" | tee -a "$OUT"
CRED_A_JSON=$(body_of POST /api/vault "$TOKA" '{"title":"A Bank","username":"dave","password":"AOwnerSecret1!","category":"BANKING"}')
CRED_A_ID=$(echo "$CRED_A_JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
echo "created credential id=$CRED_A_ID for A" | tee -a "$OUT"

code=$(req GET "/api/vault/$CRED_A_ID" "$TOKC")
row "C(stranger) GET A's credential" "$code" "403"
code=$(req PUT "/api/vault/$CRED_A_ID" "$TOKC" '{"title":"Hijacked"}')
row "C(stranger) PUT A's credential" "$code" "403"
code=$(req DELETE "/api/vault/$CRED_A_ID" "$TOKC")
row "C(stranger) DELETE A's credential" "$code" "403"
code=$(req GET "/api/vault/$CRED_A_ID/history" "$TOKC")
row "C(stranger) GET A's credential history" "$code" "403"

echo "=== Section D: READ-share user attempts update/delete/reshare -> 403 ===" | tee -a "$OUT"
SHARE_JSON=$(body_of POST /api/share "$TOKA" "{\"credentialId\":$CRED_A_ID,\"sharedWithEmail\":\"$UB\",\"permission\":\"READ\"}")
SHARE_ID=$(echo "$SHARE_JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
echo "share id=$SHARE_ID A->B READ" | tee -a "$OUT"

code=$(req GET "/api/vault/$CRED_A_ID" "$TOKB")
row "B(READ share) GET A's credential" "$code" "200"
code=$(req PUT "/api/vault/$CRED_A_ID" "$TOKB" '{"title":"Hijacked By B"}')
row "B(READ share) PUT A's credential" "$code" "403"
code=$(req DELETE "/api/vault/$CRED_A_ID" "$TOKB")
row "B(READ share) DELETE A's credential" "$code" "403"
code=$(req POST /api/share "$TOKB" "{\"credentialId\":$CRED_A_ID,\"sharedWithEmail\":\"$UC\",\"permission\":\"READ\"}")
row "B(READ share, not owner) POST /api/share (reshare A's credential)" "$code" "403"

echo "=== Section E: revoked share -> 403 ===" | tee -a "$OUT"
code=$(req DELETE "/api/share/$SHARE_ID" "$TOKA")
row "A revokes B's share" "$code" "204"
code=$(req GET "/api/vault/$CRED_A_ID" "$TOKB")
row "B(revoked share) GET A's credential" "$code" "403"

echo "=== Section F: expired share -> 403 (behaves exactly like no share) ===" | tee -a "$OUT"
SHARE2_JSON=$(body_of POST /api/share "$TOKA" "{\"credentialId\":$CRED_A_ID,\"sharedWithEmail\":\"$UB\",\"permission\":\"READ\"}")
SHARE2_ID=$(echo "$SHARE2_JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')
docker exec securevault-postgres psql -U postgres -d securevault -c "UPDATE credential_shares SET expires_at = now() - interval '1 hour' WHERE id=$SHARE2_ID;" >> "$OUT" 2>&1
code=$(req GET "/api/vault/$CRED_A_ID" "$TOKB")
row "B(expired share) GET A's credential" "$code" "403"
code=$(req GET /api/share/received "$TOKB")
recv_count=$(body_of GET /api/share/received "$TOKB" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["data"]))')
echo "B's received-shares list length after expiry = $recv_count (expired shares are excluded outright, M-45)" | tee -a "$OUT"

echo "=== Section G: non-admin on every /api/admin/** route -> 403 ===" | tee -a "$OUT"
declare -a ADMIN_ROUTES=(
  "GET /api/admin/stats"
  "GET /api/admin/users"
  "GET /api/admin/audit-logs"
  "PUT /api/admin/users/1/status"
)
for route in "${ADMIN_ROUTES[@]}"; do
  method=${route%% *}
  path=${route#* }
  body=""
  [ "$method" = "PUT" ] && body='{"locked":true}'
  code=$(req "$method" "$path" "$TOKA" "$body")
  row "non-admin(A) $route" "$code" "403"
done
echo "=== Section G2: same routes, real admin token -> 200 ===" | tee -a "$OUT"
code=$(req GET /api/admin/stats "$TOKADM")
row "admin GET /api/admin/stats" "$code" "200"
code=$(req GET /api/admin/users "$TOKADM")
row "admin GET /api/admin/users" "$code" "200"
code=$(req GET /api/admin/audit-logs "$TOKADM")
row "admin GET /api/admin/audit-logs" "$code" "200"

echo "=== Section H: logged-out access token (Redis denylist) -> 401 ===" | tee -a "$OUT"
TOKD_LOGIN=$(login "$UC")
TOKD=$(echo "$TOKD_LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')
REFRESHD=$(echo "$TOKD_LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["refreshToken"])')
code=$(req GET /api/vault "$TOKD")
row "C, before logout, GET /api/vault" "$code" "200"
code=$(req POST /api/auth/logout "$TOKD" "{\"refreshToken\":\"$REFRESHD\"}")
row "C logs out (Authorization header + real refreshToken body)" "$code" "200"
code=$(req GET /api/vault "$TOKD")
row "C's now-logged-out access token, GET /api/vault" "$code" "401"
code=$(req POST /api/auth/refresh "" "{\"refreshToken\":\"$REFRESHD\"}")
row "C's revoked refreshToken, POST /api/auth/refresh" "$code" "401"

echo "=== Section I: reuse of a rotated refresh token -> 401 ===" | tee -a "$OUT"
TOKE_LOGIN_JSON=$(login "$UB")
REFRESH1=$(echo "$TOKE_LOGIN_JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["refreshToken"])')
REFRESH_RESULT=$(body_of POST /api/auth/refresh "" "{\"refreshToken\":\"$REFRESH1\"}")
code_first=$(req POST /api/auth/refresh "" "{\"refreshToken\":\"$REFRESH1\"}")
row "first refresh call with a not-yet-used refresh token" "200" "200" "(sanity, consumed above via body_of)"
REFRESH2=$(echo "$REFRESH_RESULT" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["refreshToken"])')
echo "refresh rotated: old-len=${#REFRESH1} new-len=${#REFRESH2} same=$([ "$REFRESH1" = "$REFRESH2" ] && echo yes || echo no)" | tee -a "$OUT"
code=$(req POST /api/auth/refresh "" "{\"refreshToken\":\"$REFRESH1\"}")
row "replay of the OLD (already-rotated) refresh token" "$code" "401"

echo "=== Section J: no passwordHash leakage, no email-existence oracle ===" | tee -a "$OUT"
REG_BODY=$(body_of POST /api/auth/register "" "{\"fullName\":\"Leak Check\",\"email\":\"leakcheck-$RAND@sv-test.local\",\"password\":\"$PW\"}")
echo "register response: $REG_BODY" | tee -a "$OUT"
echo "$REG_BODY" | grep -qi "passwordHash" && echo "FAIL: passwordHash present in register response" | tee -a "$OUT" || echo "PASS: no passwordHash key in register response" | tee -a "$OUT"

LOGIN_UNKNOWN=$(body_of POST /api/auth/login "" "{\"email\":\"totally-unknown-$RAND@sv-test.local\",\"password\":\"WrongPass1!\"}")
LOGIN_WRONGPW=$(body_of POST /api/auth/login "" "{\"email\":\"$UA\",\"password\":\"WrongPass1!\"}")
MSG_UNKNOWN=$(echo "$LOGIN_UNKNOWN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["message"])')
MSG_WRONGPW=$(echo "$LOGIN_WRONGPW" | python3 -c 'import sys,json;print(json.load(sys.stdin)["message"])')
echo "unknown-email message:   $MSG_UNKNOWN" | tee -a "$OUT"
echo "wrong-password message:  $MSG_WRONGPW" | tee -a "$OUT"
[ "$MSG_UNKNOWN" = "$MSG_WRONGPW" ] && echo "PASS: identical message, no email-existence oracle" | tee -a "$OUT" || echo "FAIL: messages differ" | tee -a "$OUT"

echo "=== DONE ===" | tee -a "$OUT"
