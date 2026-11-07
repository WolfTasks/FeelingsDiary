#!/usr/bin/env bash
# Sourced by scripts/pre-commit (do not execute directly).
# Picks a JDK that ships jlink (IntelliJ JBRs often lack it, which breaks the Android build).
# On success exports JAVA_HOME and returns 0; on failure prints a message and returns 1
# (return instead of exit, because exit would kill the shell that sourced this file).

_has_jlink() {
    [ -n "${1:-}" ] && { [ -x "$1/bin/jlink" ] || [ -x "$1/bin/jlink.exe" ]; }
}

if ! _has_jlink "${JAVA_HOME:-}"; then
    unset JAVA_HOME
    for candidate in \
        "/c/Program Files/Eclipse Adoptium"/jdk-21* \
        "$HOME/.jdks"/temurin-21* \
        "$HOME/.jdks"/*21* \
        "/c/Program Files/Android/Android Studio/jbr" \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
        if _has_jlink "$candidate"; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

unset -f _has_jlink

if [ -z "${JAVA_HOME:-}" ]; then
    echo "✗ Kein JDK mit jlink gefunden – JAVA_HOME auf JDK 21 setzen"
    return 1
fi

echo "JDK: $JAVA_HOME"
