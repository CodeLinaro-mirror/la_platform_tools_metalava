#! /bin/sh

# 'strict' mode
set -euo pipefail

SOURCE_BRANCH=${1-aosp/metalava-main}

echo -n "Checking status"
STATUS=$(git status -s | grep -v "??" || true)
if [[ $STATUS ]]; then
  echo " - ERROR"
  echo
  echo "The following changed files would interfere with merge:"
  echo "$STATUS"
  exit 1
else
  echo " - OK"
fi

echo -n "Checking current branch"
BRANCH=$(git branch --show-current)
if [[ $BRANCH ]]; then
  echo " - OK ($BRANCH)"
else
  echo " - ERROR"
  echo
  echo "No branch found, please run 'repo start <branch>'."
  exit 1
fi

echo -n "Current upstream branch"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref --symbolic-full-name @{u})
echo " - ${CURRENT_BRANCH}"

function output_only_on_failure() {
  set +e
  OUTPUT=$(eval "$@" 2>&1)
  EXIT_CODE=$?
  set -e
  if [[ $EXIT_CODE != 0 ]]; then
    echo " - ERROR"
    echo "Command: $@"
    echo $OUTPUT
    exit 1
  fi
}

# Make sure that ${SOURCE_BRANCH} is up to date.
echo -n "Making sure that ${SOURCE_BRANCH} is up to date"
SPLIT_REPOSITORY_BRANCH=${SOURCE_BRANCH/\// }
output_only_on_failure git fetch ${SPLIT_REPOSITORY_BRANCH}
echo " - OK"

echo -n "Checking to see if there is anything to merge"
if git merge-base --is-ancestor "${SOURCE_BRANCH}" HEAD; then
  echo " - NOTHING TO DO"
  exit 0
else
  echo " - CHANGES FOUND"
fi

# Collect upstream-upgrade merge commits (created by external_updater with
# UPSTREAM_REV_ID:) whose upstream parent is not already an ancestor of HEAD,
# while excluding raw upstream commits reachable from those second parents and
# keeping branch-sync merge commits excluded so their Bug/Change-Id trailers are
# not duplicated.
UPGRADE_MERGES=()
EXCLUDE_UPSTREAM=()
for MERGE_COMMIT in $(git rev-list "HEAD..${SOURCE_BRANCH}" --merges --grep="^ *UPSTREAM_REV_ID:"); do
  EXCLUDE_UPSTREAM+=("^${MERGE_COMMIT}^2")
  if ! git merge-base --is-ancestor "${MERGE_COMMIT}^2" HEAD; then
    UPGRADE_MERGES+=("${MERGE_COMMIT}")
  fi
done

function merged_commit_logs() {
  git log "HEAD..${SOURCE_BRANCH}" --no-merges ${EXCLUDE_UPSTREAM[@]+"${EXCLUDE_UPSTREAM[@]}"}
  if [[ ${#UPGRADE_MERGES[@]} -gt 0 ]]; then
    git show -s "${UPGRADE_MERGES[@]}"
  fi
}

echo -n "Extracting bugs from merged in changes"
BUGS=$(merged_commit_logs | (grep -E "^ *Bug: *[0-9]+" || true) | sed "s/Bug://" | sort -u -n)
echo " - DONE"

echo -n "Extracting change list from merged in changes"

# Get the Change-Id from the merged changes.
# This uses the Change-Id rather than the SHA as the Change-Id is consistent
# across branches but the SHA might not be.
CHANGE_IDS=$(merged_commit_logs | (grep -E "^ *Change-Id: I*[0-9a-f]+" || true) | sed "s/^ *Change-Id: //")

trap 'rm -f "${MESSAGE_FILE-}" "${GERRIT_ERR-}"' EXIT

if [[ -n "${CHANGE_IDS}" ]]; then
  # Generate a query which will find only those changes which are from metalava-main.
  # All changes to tools/metalava must come from metalava-main or main (build changes).
  QUERY="(branch:metalava-main or branch:main) and ($(echo $CHANGE_IDS | sed 's/ / or /g')) and status:merged"

  # Generate a list of changes to insert in the commit message.
  # This queries the Android Gerrit as metalava development is always done in AOSP.
  GERRIT_ERR=$(mktemp)
  if ! GERRIT_OUTPUT=$(/google/data/ro/projects/android/gerrit -g android -r --custom_raw_format '{o.number} {o.subject}' search "$QUERY" 2>"${GERRIT_ERR}"); then
    echo " - ERROR"
    [[ -z "${GERRIT_OUTPUT}" ]] || echo "${GERRIT_OUTPUT}"
    cat "${GERRIT_ERR}"
    exit 1
  fi
  CHANGE_LIST=$(printf '%s\n' "${GERRIT_OUTPUT}" | while read -r NUMBER SUBJECT
  do
    [[ -n "${NUMBER}" ]] || continue
    echo "* $SUBJECT"
    echo "  - https://r.android.com/$NUMBER"
  done)
else
  CHANGE_LIST=""
fi
echo " - DONE"

echo -n "Performing the merge"
MESSAGE_FILE=$(mktemp)

SCRIPT_PATH=${0##*metalava/}

cat > ${MESSAGE_FILE} <<EOF
Merge remote-tracking branch '${SOURCE_BRANCH}' into '${CURRENT_BRANCH}'

Merge performed by:
  ${SCRIPT_PATH}${1+ $@}

Changes includes in this merge (from newest to oldest):
${CHANGE_LIST}

This merge includes a number of changes so this contains a list of all
the affected bugs in order from oldest to newest.

$(for BUG in $BUGS; do echo "Bug: $BUG"; done)
Test: m checkapi
EOF

output_only_on_failure git merge ${SOURCE_BRANCH} --no-ff -F ${MESSAGE_FILE}

echo " - DONE"
echo "The merge commit has been created. Please do the following before uploading:"
echo "1. Verify the commit by running 'm checkapi'"
echo "2. Review the commit to make sure it includes what is expected"

