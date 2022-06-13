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


KEYS=`git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq`

LABLE="HOTFIX"

if [ "${PURPOSE}" = "saas" ]
then
    LABLE="SAAS_HOTFIX"
    FIELD_ID="customfield_10644"
    HOTFIX_FIELD="customfield_10773"
elif [ "${PURPOSE}" = "on-prem" ]
then
    LABLE="ONPREM_HOTFIX"
    FIELD_ID="customfield_10646"
    HOTFIX_FIELD="customfield_10775"
else
   echo "Unknown purpose ${PURPOSE}"
   exit 1
fi

echo $KEYS

echo $LABLE


for KEY in ${KEYS}
do
    echo $KEY
    curl \
       -X PUT \
       --data "{ \"update\": { \"labels\": [ {\"add\": \"${LABLE}\"} ] },\"fields\" : { \"${FIELD_ID}\" : \"${VERSION}\", \"${HOTFIX_FIELD}\" : \"${VERSION}\" }}" \
       -H "Content-Type: application/json" \
       https://harness.atlassian.net/rest/api/2/issue/${KEY} \
       --user $JIRA_USERNAME:$JIRA_PASSWORD
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
  EXCLUDE_PROJECTS=",ART,CCE,CDC,CDNG,CDP,CE,COMP,CV,CVNG,CVS,DX,ER,GIT,GTM,LWG,OENG,ONP,OPS,SEC,SWAT,"
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
      response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "C>        "update": {
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
