#!/bin/sh
API_BASE="${EVENTORE_API_BASE_URL:-/api/v1}"
WS_URL="${EVENTORE_WS_URL:-}"
if [ -z "$WS_URL" ]; then
  WS_URL="ws://${EVENTORE_HOST:-localhost}/ws/stream"
fi
cat > /usr/share/nginx/html/eventore-config.js <<EOF
window.__EVENTORE_CONFIG__ = {
  apiBaseUrl: "${API_BASE}",
  wsUrl: "${WS_URL}"
};
EOF
sed -i 's|</head>|<script src="/eventore-config.js"></script></head>|' /usr/share/nginx/html/index.html 2>/dev/null || true
