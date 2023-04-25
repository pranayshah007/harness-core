#!/bin/bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
#
# Author: marc.batchelor@harness.io
# BT-3430
# DEBUG="true"
#########################
# Update the Jira Label #
#########################
function update_jira_label() {
  if [[ -z "$1" ]]; then
    echo "PR Title missing Jira case - Exiting"
    exit 1
  fi

  response=$(curl -X PUT https://harness.atlassian.net/rest/api/2/issue/$1 --write-out '%{http_code}' --output /dev/null --silent --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
    "update": {
      "labels" : [ 
        { "add": "'"$2"'" }
      ] } }'
  )

  if  [[ "$response" -ne 204 ]] ; then
    echo "Failed to add label to $1 - Response code: $response"
    if [[ "$response" -eq 404 ]] ; then
      echo "404 response indicates that user $JIRA_USERNAME does not have permissions to update labels on $1"
    fi
  else
    echo "Successfully added label to $1"
  fi
}

#######################################
# Check for Module Dependency Changes #
#######################################
function check_for_dependency() {
  checkresult="false"
  if [[ -z "$1" ]]; then
    echo "Missing PR Number to check"
    exit 1
  fi
  
  resp=$(curl -L -s "https://patch-diff.githubusercontent.com/raw/harness/harness-core/pull/$1.diff" --write-out '%{http_code}' --output pr.diff)
  if [ $? -eq 0 ];
  then
    if [ $resp -ne 200 ];
    then
        echo "Could not download ${ghprbPullId}.diff from github"
        exit 1
    fi
  else
    echo "curl command malformed."
    exit 1
  fi

  # Extract changed files
  # Converts this:
  #   diff --git a/931-delegate-decryption-service/src/java/io/harness/decryption/delegate/BUILD.bazel b/931-delegate-decryption-service/src/java/io/harness/decryption/delegate/BUILD.bazel
  # To this:
  #   931-delegate-decryption-service/src/java/io/harness/decryption/delegate/BUILD.bazel
  cat pr.diff | grep "^diff " | sed 's/diff --git a\///' | sed 's/ b\/.*$//' > $tmpdiff

  if [ "$DEBUG" == "true" ]; then cat $tmpdiff; fi

  # Compare each line in tmpdif (list of file changes) to the cleaned outputs from bazel
  awk 'NR==FNR {a[$0]; next} $1 in a' $tmpdiff $tmpdeps>$tmpawk
  
  anyfound=$(cat $tmpawk | wc -l)
  if [ $anyfound != "0" ];
  then
    echo "**PR $1 HAS dependency changes**"
    cat $tmpawk
    checkresult="true"
    if [[ -z "$2" ]]; then
      echo "No Jira key provided - skipping Jira update"
    else
      update_jira_label $2 $label
    fi
  else
    echo "PR $1 does NOT have dependency changes"
    checkresult="false"
  fi
}

#################################
# Run Tests on "known" PR diffs #
#################################
function runPRTests() {
  positive=("45911" "45770" "45426" "44756" "46180" "46768" "45409" "44781")
  negative=("46381" "46327" "46271" "45585")

  echo "***** Running Positive Tests *****"
  for apos in ${positive[@]}; do
    check_for_dependency $apos
    if [ "$checkresult" != "true" ]; then
      echo "Test Failed - $apos should have found dependency change!"
      exit 1
    fi
  done
  echo "***** Running negative Tests *****"
  for aneg in ${negative[@]}; do
    check_for_dependency $aneg
    if [ "$checkresult" != "false" ]; then
      echo "Test Failed - $aneg should NOT have found dependency change!"
      exit 1
    fi
  done
}

ps -p $$
echo $SHELL
echo $0
readlink /proc/$$/exe
cat /etc/shells

set +e

checkresult="false"

if [[ -z "$1" ]]; then
  echo "Expect module to be passed as first argument"
  exit 1 
fi
module=$1

if [[ -z "$2" ]]; then
  echo "Expect Label to be passed as second argument"
  exit 1 
fi
label=$2

echo "ghprbPullTitle=$ghprbPullTitle"
echo "ghprbPullId=$ghprbPullId"
COMMIT_CONTENT="\[feat]|\[fix]|\[techdebt]|\[hotfixpreqa]|feat|fix|techdebt|hotfixpreqa"
SHDIR=`dirname "$0"`
PROJECTS=$(<$SHDIR/jira-projects.txt)

PR_MESSAGE=`echo "${ghprbPullTitle}" | grep -iE "^(${COMMIT_CONTENT}[\ ]*):[\ ]*\[(${PROJECTS})-[0-9]+][:\ ]*"`
echo "PR_MESSAGE=$PR_MESSAGE"
KEY=$(echo "${ghprbPullTitle}" | grep -o -iE '('$PROJECTS')-[0-9]+')
echo "KEY=$KEY"
if [ "$KEY" == "" ]; then
  echo "PR Title doesn't have valid Jira Key - existing"
  exit 1
fi

# Make sure that bazel can "talk" to the cache
export GOOGLE_CREDENTIALS_FILE=/tmp/gcp_creds.json
scripts/bazel/generate_credentials.sh
cat test-util.bzl
touch .bazelignore
echo "200-functional-test" >> .bazelignore
echo "190-deployment-functional-tests" >> .bazelignore
echo "//product/..." >> .bazelignore
echo "//commons/..." >> .bazelignore
cat .bazelignore 

# temp file for the awk output
tmpawk=$(mktemp -q /tmp/awktemp.XXXXXX)
# temp file for the dependencies
tmpdeps=$(mktemp -q /tmp/depstemp.XXXXXX)
# temp file for the diff file names
tmpdiff=$(mktemp -q /tmp/diff.XXXXXX)
# Delete files on exit
trap 'rm -f -- "$tmpawk"' EXIT
trap 'rm -f -- "$tmpdeps"' EXIT
trap 'rm -f -- "$tmpdiff"' EXIT

# Query dependencies, and use sed to clean the output
#
# The sed's below change:
#  //420-delegate-agent:module
#  //420-delegate-agent:src/main/java/io/harness/Dummy420.java
#
# Into this:
#  420-delegate-agent/src/main/java/io/harness/Dummy420.java
#
# first sed removes all lines beginning with "@"
# second sed removes :module lines
# third sed removes leading '//'
# fourth sed removes the colon
# fifth sed removes empty lines if they exist

bazel query "deps(//$module:module)" | grep -v  ^@ | sed '/^.*:module$/d; s/$\/\///; s/\/\///; s/:/\//; /^$/d'> $tmpdeps

if [ "$DEBUG" == "true" ]; then tail -n100 $tmpdeps; fi

if [ "$runtests" != "true" ]; then
  check_for_dependency ${ghprbPullId} ${KEY}
else
  runPRTests
fi
