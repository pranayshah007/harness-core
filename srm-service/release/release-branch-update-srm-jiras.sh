#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

KEYS=$(git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE 'SRM-[0-9]+' | sort | uniq)
echo $KEYS

#FIX_SRM_VERSION value to be same as used in release-branch-create-srm-version.sh
FIX_SRM_VERSION="SRM_""$VERSION"00

for KEY in ${KEYS}
  do
    echo "$KEY"
    response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
    "update": {
    "fixVersions": [
      {"add":
        {"name": "'"$FIX_SRM_VERSION"'" }
      }]}}')
    if [[ "$response" -eq 204 ]] ; then
      echo "$KEY fixVersion set to $FIX_SRM_VERSION"
    elif [[ "$response" -eq 400 ]] ; then
      echo "Could not set fixVersion on $KEY - field hidden for the issue type"
    fi
  done