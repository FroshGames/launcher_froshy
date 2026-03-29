#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ -f "$SCRIPT_DIR/launcher.sh" ]; then
    exec bash "$SCRIPT_DIR/launcher.sh" "$@"
fi

echo "No se encontro launcher.sh en $SCRIPT_DIR"
exit 1

