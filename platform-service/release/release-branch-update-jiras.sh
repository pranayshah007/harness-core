#!/bin/bash

set -ex
KEYS=$(git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE '('PL')-[0-9]+' | sort | uniq)

echo $KEYS

#FIX_PLS_VERSION value to be same as used in release-branch-create-pls-version.sh
FIX_PLS_VERSION="PlatformService_""$VERSION"00

for KEY in ${KEYS}
  do
    echo "$KEY"
    PARAMS="11198,10366" # notifications,audit-trail
    IFS="," read -ra EXP_COMPONENTS <<< "$PARAMS"
    COMPONENT_IDS=$(curl -X GET -H "Content-Type: application/json" \
      https://harness.atlassian.net/rest/api/2/issue/$KEY?fields=components \
      --user $JIRA_USERNAME:$JIRA_PASSWORD \
      | jq -c '.fields.components[].id' | tr -d '"')
      # shellcheck disable=SC2068
    for comp in ${COMPONENT_IDS[@]}
    do
      for exp_comp in ${EXP_COMPONENTS[@]}
      do
        if [[ $comp == $exp_comp ]]; then
          response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
          "update": {
          "fixVersions": [
            {"add":
              {"name": "'"$FIX_PLS_VERSION"'" }
            }]}}')
          if [[ "$response" -eq 204 ]] ; then
            echo "$KEY fixVersion set to $FIX_PLS_VERSION"
          elif [[ "$response" -eq 400 ]] ; then
            echo "Could not set fixVersion on $KEY - field hidden for the issue type"
          fi
          break 2
        fi
      done
    done
  done