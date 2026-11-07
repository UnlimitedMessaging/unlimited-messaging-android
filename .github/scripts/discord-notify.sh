#!/usr/bin/env bash
# Posts an embed to the Discord webhook in $DISCORD_WEBHOOK.
# Usage: discord-notify.sh <success|fail|neutral> <title> <description> [url]
# Silently does nothing when the secret is not set (forks, local runs).
set -euo pipefail

status="$1"; title="$2"; description="$3"; url="${4:-}"
if [ -z "${DISCORD_WEBHOOK:-}" ]; then
  echo "DISCORD_WEBHOOK not set, skipping notification"
  exit 0
fi

case "$status" in
  success) color=482900 ;;   # #075E54, UM green
  fail)    color=15158332 ;; # red
  *)       color=1214846 ;;  # #128C7E teal
esac

payload=$(jq -n \
  --arg title "$title" \
  --arg description "$description" \
  --arg url "$url" \
  --arg repo "${GITHUB_REPOSITORY:-local}" \
  --arg ref "${GITHUB_REF_NAME:-local}" \
  --arg sha "${GITHUB_SHA:0:7}" \
  --arg actor "${GITHUB_ACTOR:-local}" \
  --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --argjson color "$color" \
  '{
    username: "UM Android",
    embeds: [{
      title: $title,
      description: $description,
      url: (if $url == "" then null else $url end),
      color: $color,
      fields: [
        { name: "Repository", value: $repo, inline: true },
        { name: "Branch", value: $ref, inline: true },
        { name: "Commit", value: $sha, inline: true },
        { name: "By", value: $actor, inline: true }
      ],
      timestamp: $timestamp
    }]
  }')

curl -fsS -H "Content-Type: application/json" -d "$payload" "$DISCORD_WEBHOOK" >/dev/null
