#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# --git-common-dir works in worktrees too (there .git is a file)
HOOKS_DIR="$(cd "$REPO_ROOT" && git rev-parse --path-format=absolute --git-common-dir)/hooks"
mkdir -p "$HOOKS_DIR"
cp "$SCRIPT_DIR/pre-commit" "$HOOKS_DIR/pre-commit"
chmod +x "$HOOKS_DIR/pre-commit"

echo "✓ Pre-Commit-Hook installiert: $HOOKS_DIR/pre-commit"
echo "  Läuft automatisch vor jedem 'git commit' auf Kotlin-Dateien."
