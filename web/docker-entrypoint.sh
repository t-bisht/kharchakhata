#!/bin/sh
# Regenerate /env.js from container env vars, then exec nginx.
# Values here are public — safe to expose in the browser bundle.

set -eu

cat > /usr/share/nginx/html/env.js <<EOF
window.ENV = {
  API_BASE_URL: "${API_BASE_URL:-/api}",
  GOOGLE_CLIENT_ID: "${GOOGLE_CLIENT_ID:-}"
};
EOF

exec nginx -g "daemon off;"
