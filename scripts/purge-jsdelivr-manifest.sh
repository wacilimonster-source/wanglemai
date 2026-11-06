#!/usr/bin/env bash
# Purge jsDelivr's cached branch manifest after pushing update/latest.json.
# Run after: git push origin main
set -euo pipefail
curl -fsSL "https://purge.jsdelivr.net/gh/wacilimonster-source/wanglemai@main/update/latest.json"
