#!/bin/bash
set -e

# This script updates trust entries in gradle/verification-metadata.xml

# Usage: $0

# The list of tasks whose dependencies need to be considered when generating the
# verification data.
task="ktFormat ktCheck test publishMetalavaPublicationToMavenRepository"

function usage() {
  echo "Usage: $0"
  exit 1
}

if [ "$#" != "0" ]; then
  usage
fi

function runGradle() {
  echo running ./gradlew "$@"
  if ./gradlew "$@"; then
    echo succeeded: ./gradlew "$@"
  else
    echo failed: ./gradlew "$@"
    return 1
  fi
}

function cleanGradleState() {
  echo "Stopping Gradle daemons"
  runGradle --stop || true
  echo

  backupDir=~/metalava-build-state-backup
  ./scripts/backup-state.sh "$backupDir" --move # prints that it is saving state into this dir"

  echo "To restore this state later, run:"
  echo
  echo "  ./scripts/restore-state.sh $backupDir"
  echo
}

# This script regenerates signature-related information (dependency-verification-metadata and keyring)
function regenerateVerificationMetadata() {
  echo "regenerating verification metadata and keyring"
  # regenerate metadata
  # Need to run a clean build, https://github.com/gradle/gradle/issues/19228
  cleanGradleState

  # Reset the metadata file to the template to prevent any build up of unnecessary
  # entries over time.
  cp scripts/verification-metadata-template.xml gradle/verification-metadata.xml
  
  # Resolving Configurations before task execution is expected.
  runGradle --stacktrace --write-verification-metadata pgp,sha256 --export-keys --dry-run $task

  # update verification metadata file

  # first, make sure the resulting file is named "verification-metadata.xml"
  mv gradle/verification-metadata.dryrun.xml gradle/verification-metadata.xml

  # next, remove 'version=' lines https://github.com/gradle/gradle/issues/20192
  if [ "$(uname)" = "Darwin" ]; then
      sed -i '' 's/\(trusted-key.*\) version="[^"]*"/\1/' gradle/verification-metadata.xml
  else
      sed -i 's/\(trusted-key.*\) version="[^"]*"/\1/' gradle/verification-metadata.xml
  fi

  # rename keyring
  mv gradle/verification-keyring.dryrun.keys gradle/verification-keyring.keys
}
regenerateVerificationMetadata

echo
echo 'Done. Please check that these changes look correct (`git diff`)'
echo "If Gradle did not make all expected updates to verification-metadata.xml, you can try '--no-dry-run'. This is slow so you may also want to specify a task. Example: $0 --no-dry-run exportSboms"
