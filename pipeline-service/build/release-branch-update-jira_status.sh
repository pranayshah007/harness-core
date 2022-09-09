#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

SHDIR=`dirname "$0"`
PROJECTS="PIE"

KEYS=$(git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)

# QA-Test status ID is 201, Dev Complete status id is 151.
STATUS_ID=201

if [ ! -z "$STATUS_ID_TO_MOVE" ]
then
      STATUS_ID=$STATUS_ID_TO_MOVE
fi

for KEY in ${KEYS}
do

    status=`curl -X GET -H "Content-Type: application/json" https://harness.atlassian.net/rest/api/2/issue/${KEY}?fields=status --user $JIRA_USERNAME:$JIRA_PASSWORD | jq ".fields.status.name" | tr -d '"'`

    if [[ "${status}" = "QA Test" || "${status}" = "Done" || "${status}" = "Under investigation"  ]]
            then
               echo " ${KEY}  is in ${status} status, Hence no update needed"
            else
               echo " ${KEY}  is in  ${status} , Hence moving to ${STATUS_ID} status"
               curl \
                 -X POST \
                --data "{\"transition\":{\"id\":\"${STATUS_ID}\"}}" \
                -H "Content-Type: application/json" \
                https://harness.atlassian.net/rest/api/2/issue/${KEY}/transitions \
                --user $JIRA_USERNAME:$JIRA_PASSWORD
    fi

done
