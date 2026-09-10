#!/usr/bin/env sh
# Bootstrap launcher (not the official wrapper JAR). Run the wrapper task to generate it.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec sh "$ROOT/scripts/gradle-bootstrap.sh" -p "$ROOT" "$@"
