#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function check_file_present(){
     local_file=$1
     if [ ! -f "$local_file" ]; then
        echo "ERROR: $LINENO: File $local_file not found. Exiting"
        exit 1
     fi
}

set -x
#SHDIR=$(dirname "$0")
#PROJFILE="$SHDIR/jira-projects.txt"
#check_file_present $PROJFILE
PROJECTS="PIE"
KEYS=$(git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)
#Getting the dummy commits that are made to the branch and creating a ticket out of it
git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -iE "\[(${PROJECTS})-0]:.*" -o | sort | uniq  | tr '\n' ',' > dummyJiraList.txt
dummyJiraList=$(sed 's/,/\\n/g' dummyJiraList.txt)

#Creating a ticket for such tickets
if [ -s dummyJiraList.txt ]
then
  response=$(curl -X POST \
  https://harness.atlassian.net/rest/api/2/issue/ \
  --user $JIRA_USERNAME:$JIRA_PASSWORD \
  -H 'content-type: application/json' \
  -d '{
    "fields": {
       "project":
       {
          "key": "ART"
       },
       "summary": "Contains the details of untracked changes going all the release build:'$VERSION'00",
       "description": "'{code}"$dummyJiraList"{code}'",
       "issuetype": {
          "name": "Story"
       }
   }
}')
  ticketId=$(echo $response | grep -o -iE '(ART)-[0-9]+')
else
  echo "No Dummy Commits"
fi


#Assigning the Release BE Number to this ticket
if [ -z "$ticketId" ]
then
  echo "No Ticket is Created as there are no dummy commits"
else
  KEYS="${KEYS} ${ticketId}"
fi

#Updating all the Jira tickets with the Release Build Number
if [ "${PURPOSE}" = "pipeline-service" ]
then
    FIELD_ID="customfield_10644"
elif [ "${PURPOSE}" = "on-prem" ]
then
    FIELD_ID="customfield_10646"
else
   echo "Unknown purpose ${PURPOSE}"
   exit 1
fi
for KEY in ${KEYS}
do
    echo $KEY
    curl \
       -X PUT \
       --data "{ \"fields\" : { \"${FIELD_ID}\" : \"${VERSION}\" }}" \
       -H "Content-Type: application/json" \
       https://harness.atlassian.net/rest/api/2/issue/${KEY} \
       --user $JIRA_USERNAME:$JIRA_PASSWORD
done