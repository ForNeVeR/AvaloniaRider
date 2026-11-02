#!/usr/bin/env bash
# Runs the harness over every view with an x:Class under a project directory.
# usage: sweep.sh <projectDir> <targetDir> <targetName> <xamlAssembly> <previewerHost> <outputFile>
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
export PROJECT=$1 TARGET_DIR=$2 TARGET_NAME=$3 XAML_ASSEMBLY=$4 HOST_DLL=$5
export HARNESS=$ROOT/scripts/DesignHookHarness/bin/Release/net10.0/DesignHookHarness.dll
export HOOK=$ROOT/src/dotnet/AvaloniaRider.DesignHook/bin/AvaloniaRider.DesignHook/Debug/AvaloniaRider.DesignHook.dll
one() {
  rel=${1#"$PROJECT"}
  timeout 150 dotnet "$HARNESS" "$HOST_DLL" "$TARGET_DIR" "$TARGET_NAME" "$XAML_ASSEMBLY" "$1" "$rel" "$HOOK" 48 2>&1 | tail -1
  echo "   exit=${PIPESTATUS[0]}"
}
export -f one
grep -rl --include=*.axaml 'x:Class' "$PROJECT" --exclude-dir=bin --exclude-dir=obj | sort \
  | xargs -P 4 -I{} bash -c 'r=$(one "$1"); echo "$r" | tr "\n" " "; echo' _ {} > "$6"
