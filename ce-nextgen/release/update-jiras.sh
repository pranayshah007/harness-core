#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# The following are configured in the branch cut pipeline
# PREVIOUS_CUT_COMMIT_MESSAGE, FIX_VERSION, JIRA_USERNAME, JIRA_PASSWORD

set -ex
KEYS=$(git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE '('CCM')-[0-9]+' | sort | uniq)

echo $KEYS


for KEY in ${KEYS}
  do
    echo "$KEY"
    # Extract Jira project from Jira key
    IFS="-" read -ra PROJNUM <<< "$KEY"
    PROJ="${PROJNUM[0]}"
    response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
      "update": {
        "fixVersions": [
          {"add":
            {"name": "'"$FIX_VERSION"'" }
          }
        ]
      }
    }')

    if [[ "$response" -eq 204 ]] ; then
      echo "$KEY fixVersion set to $FIX_VERSION"
    elif [[ "$response" -eq 400 ]] ; then
      echo "Could not set fixVersion on $KEY - field hidden for the issue type"
    fi
  done
