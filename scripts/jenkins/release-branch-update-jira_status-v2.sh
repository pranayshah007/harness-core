#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

SHDIR=`dirname "$0"`
PROJECTS=$(<$SHDIR/jira-projects.txt)

#KEYS=$(git log --pretty=oneline --abbrev-commit |\
#      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
#      grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)
KEYS=$(git log --pretty=oneline --format="%s" --abbrev-commit ${PREVIOUS_RELEASE_BRANCH}..${CURRENT_RELEASE_BRANCH} | grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)

EXCLUSION_KEYS=$(git log --pretty=oneline --format="%s" --abbrev-commit ${PREVIOUS_RELEASE_BRANCH}..${CURRENT_RELEASE_BRANCH} | grep -o -iE 'HotfixPreQA: [('$PROJECTS')-[0-9]+]' | grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)

EXCLUDED_KEYS_LIST=""

#checking if Pre QA hotfix already contains RELEASE_BE_SAAS or not
for KEY in ${EXCLUSION_KEYS}
do
    EXCLUDE_PROJECTS=",PIE,IDP,"
    echo "Excluded Key - $KEY"
    IFS="-" read -ra PROJNUM <<< "$KEY"
    PROJ="${PROJNUM[0]}"
    # If it is in the exclude projects list, then do not attempt to set the version
    if [[ $EXCLUDE_PROJECTS == *",$PROJ,"* ]]; then
      echo "Skipping $KEY - project is archived or not relevant to versions."
    else
      RELEASE_BE_SAAS=`curl -X GET -H "Content-Type: application/json" https://harness.atlassian.net/rest/api/2/issue/${KEY} --user $JIRA_USERNAME:$JIRA_PASSWORD | jq ".fields.customfield_10644" | tr -d '"'`
       if [[ -z ${RELEASE_BE_SAAS} ]]; then
         echo "RELEASE_BE_SAAS is null"
       else
        EXCLUDED_KEYS_LIST="${EXCLUDED_KEYS_LIST} ${KEY}"
       fi
    fi
done


#Removing EXCLUDED_KEYS_LIST from the original KEYS List in order to avoid tagging them.
for EXCLUDE_KEY in ${EXCLUDED_KEYS_LIST}; do
KEYS=( "${KEYS[@]/$EXCLUDE_KEY}" )
done

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
