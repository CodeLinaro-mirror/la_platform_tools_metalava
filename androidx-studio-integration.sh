#!/bin/bash
set -e

# Use this flag to temporarily disable the metalava studio integration target, if it is known that it will be failing
# for some extended time.
METALAVA_STUDIO_INTEGRATION_ENABLED=false

if $METALAVA_STUDIO_INTEGRATION_ENABLED
then
  cd "$(dirname $0)/../../"
  SCRIPT_DIR="$(pwd)"
  echo "Script running from $(pwd)"

  # resolve DIST_DIR
  if [ -z "$DIST_DIR" ]; then
    DIST_DIR="$SCRIPT_DIR/out/dist"
  fi
  mkdir -p "$DIST_DIR"

  export OUT_DIR=out
  export DIST_DIR="$DIST_DIR"

  plat="linux"
  case "`uname`" in
    Darwin* )
      plat="darwin"
      ;;
  esac
  export ANDROID_HOME="$(pwd)/prebuilts/fullsdk-$plat"

  JAVA_HOME="$(pwd)/prebuilts/studio/jdk/jbr-next/linux" tools/gradlew -p tools/ publishLocal --stacktrace

  # Depend on the generated version.properties file, as the version depends on
  # the release flag
  versionProperties="$OUT_DIR/build/base/builder-model/build/resources/main/com/android/builder/model/version.properties"
  # Mac grep doesn't support -P, so use perl version of `grep -oP "(?<=buildVersion = ).*"`
  export LINT_VERSION=`perl -nle'print $& while m{(?<=baseVersion=).*}g' $versionProperties`
  export LINT_REPO="$(pwd)/out/repo"

  JAVA_HOME="$(pwd)/prebuilts/jdk/jdk21/linux-x86/" tools/gradlew -p tools/metalava \
    --no-daemon \
    --stacktrace \
     --dependency-verification=off
fi