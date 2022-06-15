#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
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
# Read file from same folder as bash script lives in
SHDIR=$(dirname "$0")
PROJFILE="$SHDIR/jira-projects.txt"
# Check that the file exists
check_file_present $PROJFILE
# Read contents of file into PROJECTS
PROJECTS=$(<$PROJFILE)
IFS="|" read -r -a PROJSPLIT <<< "${PROJECTS}"
# Set the release date of the version to the date that the build is being done.
RELDATE=$(date +'%Y-%m-%d')
# Set the next version number - this will be something like 1.23.5-704219
# Note - $VERSION and $NEW_TAG are set in release-branch.sh
if [ "${NEW_TAG}" != "" ]; then
  NEXT_VERSION=$NEW_TAG-$VERSION
else
  echo "NEW_TAG not set - just using build number"
  NEXT_VERSION=$VERSION
fi
if [ "${NEXT_VERSION}" == "" ]; then
  echo "VERSION was also empty - aborting creating Jira versions"
  exit 0
fi
# For whatever reason, VERSION doesn't have trailing zeros - so fixing it here.
NEXT_VERSION="$NEXT_VERSION""00"
echo "Creating $NEXT_VERSION in product Jira projects"
# Exclude projects that have been archived, replaced, deleted, or non-product
EXCLUDE_PROJECTS=",ART,CCE,CDC,CDNG,CDP,CE,COMP,CV,CVNG,CVS,DX,ER,GIT,GTM,LWG,OENG,ONP,OPS,SEC,SWAT,"
# Iterate over projects
for PROJ in ${PROJSPLIT[@]}
 do
    if [[ $EXCLUDE_PROJECTS == *",$PROJ,"* ]]; then
      echo "Skipping $PROJ - it has been archived or is not applicable"
    else
      # Call CURL and store http response code into $response
      response=$(curl -X POST https://harness.atlassian.net/rest/api/2/version/ --write-out '%{http_code}' --output /dev/null --silent --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
        "name": "'"$NEXT_VERSION"'",
        "releaseDate": "'"$RELDATE"'",
        "archived": false,
        "released": true }')
      # http status code 201 is "Created" - anything else is a failure
      if  [[ "$response" -ne 201 ]] ; then
        echo "Failed to create version $NEXT_VERSION in $PROJ - Response code: $response"
        if [[ "$response" -eq 404 ]] ; then
          echo "404 response indicates that user $JIRA_USERNAME does not have permissions to create versions in project $PROJ"
        fi
      else
        echo "Successfully created version $NEXT_VERSION in $PROJ"
      fi
    fi
 done
