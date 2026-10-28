#!/usr/bin/env bash
# PreToolUse-Hook für Bash-Befehle die "git push" oder "git commit" enthalten.
# - TypeScript: läuft npx tsc -b und blockt bei Fehlern (Exit 2).
# - Java: warnt bei verdächtigen Umbenennungen und zeigt alle geänderten .java-Dateien.
# Ziel: Compile-Fehler im CI abfangen, bevor sie den Build zerbrechen.

command -v jq >/dev/null 2>&1 || exit 0   # jq fehlt → Guard inaktiv, kein Block

payload="$(cat)"
bash_cmd="$(printf '%s' "$payload" | jq -r '.tool_input.command // empty' 2>/dev/null)" || exit 0
[ -z "$bash_cmd" ] && exit 0

# Nur bei git push / git commit / git push -f etc. aktiv
case "$bash_cmd" in
    *"git push"*|*"git commit"*) ;;
    *) exit 0 ;;
esac

PROJ="${CLAUDE_PROJECT_DIR:-$(pwd)}"

# ── TypeScript ────────────────────────────────────────────────────────────────
staged_ts=$(git -C "$PROJ" diff --cached --name-only 2>/dev/null | grep -E '\.(tsx?|jsx?)$' || true)
if [ -n "$staged_ts" ]; then
    echo "🔍 TypeScript-Check vor dem Push …" >&2
    if ! (cd "$PROJ/react-pc-frontend" && npx tsc -b 2>&1); then
        echo "" >&2
        echo "❌  TypeScript-Fehler gefunden – bitte beheben, dann erneut pushen." >&2
        exit 2
    fi
    echo "✅  TypeScript OK" >&2
fi

# ── Java ──────────────────────────────────────────────────────────────────────
staged_java=$(git -C "$PROJ" diff --cached --name-only 2>/dev/null | grep '\.java$' || true)
if [ -n "$staged_java" ]; then
    echo "☕  Geänderte Java-Dateien (kein lokaler Compiler – bitte Referenzen prüfen):" >&2
    echo "$staged_java" | sed 's/^/    /' >&2
    # Grobe Sicherheitsnetz: prüfe ob Variablen aus dem Diff noch im Code stehen
    # (fängt einfache Renames wie gemeinkosten → gemeinErgebnis ab)
    diff_vars=$(git -C "$PROJ" diff --cached -- '*.java' 2>/dev/null \
        | grep '^-' | grep -v '^---' \
        | grep -oE '\b[a-z][a-zA-Z0-9_]{2,}\b' \
        | sort -u || true)
    for var in $diff_vars; do
        # Wenn die Variable im alten Code vorkam aber nirgends mehr im Repo auftaucht
        count=$(git -C "$PROJ" grep -c "\b${var}\b" -- '*.java' 2>/dev/null | awk -F: '{s+=$2}END{print s+0}')
        if [ "$count" -eq 0 ]; then
            echo "⚠️   Möglicher Phantom-Verweis: '${var}' kommt nach dem Commit nirgends mehr vor." >&2
        fi
    done
    echo "" >&2
fi

exit 0
