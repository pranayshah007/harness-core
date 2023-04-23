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

SHDIR=$(dirname "$0")
PROJFILE="$SHDIR/jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

#KEYS=$(git log --pretty=oneline --abbrev-commit |\
#      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
#      grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)

KEYS=$(git log --pretty=oneline --format="%s" --abbrev-commit ${PREVIOUS_RELEASE_BRANCH}..${CURRENT_RELEASE_BRANCH} | grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)

EXCLUSION_KEYS=$(git log --pretty=oneline --format="%s" --abbrev-commit ${PREVIOUS_RELEASE_BRANCH}..${CURRENT_RELEASE_BRANCH} | grep -o -iE 'hotfixpreqa: [('$PROJECTS')-[0-9]+]' | grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)

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


#Getting the dummy commits that are made to the branch and creating a ticket out of it
#git log --pretty=oneline --abbrev-commit |\
#      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
#      grep -iE "\[(${PROJECTS})-0]:.*" -o | sort | uniq  | tr '\n' ',' > dummyJiraList.txt

#git log --pretty=oneline --format="%s" --abbrev-commit ${PREVIOUS_RELEASE_BRANCH}..${CURRENT_RELEASE_BRANCH} | grep -iE "\[(${PROJECTS})-0]:.*" -o | sort | uniq  | tr '\n' ',' > dummyJiraList.txt
#dummyJiraList=$(sed 's/,/\\n/g' dummyJiraList.txt)
#
##Creating a ticket for such tickets
#if [ -s dummyJiraList.txt ]
#then
#  response=$(curl -X POST \
#  https://harness.atlassian.net/rest/api/2/issue/ \
#  --user $JIRA_USERNAME:$JIRA_PASSWORD \
#  -H 'content-type: application/json' \
#  -d '{
#    "fields": {
#       "project":
#       {
#          "key": "ART"
#       },
#       "summary": "Contains the details of untracked changes going all the release build:'$VERSION'00",
#       "description": "'{code}"$dummyJiraList"{code}'",
#       "issuetype": {
#          "name": "Story"
#       }
#   }
#}')
#  ticketId=$(echo $response | grep -o -iE '(ART)-[0-9]+')
#else
#  echo "No Dummy Commits"
#fi
#
#
##Assigning the Release BE Number to this ticket
#if [ -z "$ticketId" ]
#then
#  echo "No Ticket is Created as there are no dummy commits"
#else
#  KEYS="${KEYS} ${ticketId}"
#fi

#Updating all the Jira tickets with the Release Build Number
if [ "${PURPOSE}" = "saas" ]
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
    EXCLUDE_PROJECTS=",PIE,CCM,IDP,"
    echo $KEY
    IFS="-" read -ra PROJNUM <<< "$KEY"
    PROJ="${PROJNUM[0]}"
    # If it is in the exclude projects list, then do not attempt to set the version
    if [[ $EXCLUDE_PROJECTS == *",$PROJ,"* ]]; then
      echo "Skipping $KEY - project is archived or not relevant to versions."
    else
      curl \
         -X PUT \
         --data "{ \"fields\" : { \"${FIELD_ID}\" : \"${VERSION}00\" }}" \
         -H "Content-Type: application/json" \
         https://harness.atlassian.net/rest/api/2/issue/${KEY} \
         --user $JIRA_USERNAME:$JIRA_PASSWORD
    fi
done
# Note - $EXECUTE_NEW_VERSION_CODE should be added to the appropriate ci Jobs for this to work
# Also, once this is rolled out for good, then this code needs to be integrated into the loop above instead
# of having two loops over the same jira cases.

if [ "${EXECUTE_NEW_VERSION_CODE}" == "true" ]; then
  # Set the next version number - this will be something like 1.23.5-704219
  # Note - $VERSION and $NEW_TAG are set in release-branch.sh
  if [ "${NEW_TAG}" != "" ]; then
    NEXT_VERSION=$NEW_TAG-$VERSION
  else
    echo "NEW_TAG not set - just using build number"
    NEXT_VERSION=$VERSION
  fi
  if [ "${NEXT_VERSION}" == "" ]; then
    echo "VERSION was also empty - aborting setting fix versions"
    exit 0
  fi
  # Version doesn't have trailing 00 - so adding it here
  NEXT_VERSION="$NEXT_VERSION""00"
  echo "Setting Fix Version to $NEXT_VERSION on issues in this release"
  EXCLUDE_PROJECTS=",ART,CCE,CDC,CDNG,CDP,CE,COMP,CV,CVNG,CVS,DX,ER,GIT,GTM,LWG,OENG,ONP,OPS,SEC,SWAT,PIE,IDP,"
  for KEY in ${KEYS}
  do
    echo "$KEY"
    # Extract Jira project from Jira key
    IFS="-" read -ra PROJNUM <<< "$KEY"
    PROJ="${PROJNUM[0]}"
    # If it is in the exclude projects list, then do not attempt to set the fix version
    if [[ $EXCLUDE_PROJECTS == *",$PROJ,"* ]]; then
      echo "Skipping $KEY - project is archived or not relevant to versions."
    else
      response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
        "update": {
          "fixVersions": [
            {"add":
              {"name": "'"$NEXT_VERSION"'" }
            }
          ]
        }
      }')
      if [[ "$response" -eq 204 ]] ; then
        echo "$KEY fixVersion set to $NEXT_VERSION"
      elif [[ "$response" -eq 400 ]] ; then
        echo "Could not set fixVersion on $KEY - field hidden for the issue type"
      fi
    fi
  done
fi
