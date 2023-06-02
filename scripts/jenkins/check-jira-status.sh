#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set +e
# Define colors
RED="\e[1m\e[91m"
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
NC="\e[0m"

export BRANCH_PREFIX=`echo ${ghprbTargetBranch} | sed 's/\(........\).*/\1/g'`
echo -e "${YELLOW}INFO: BRANCH_PREFIX=$BRANCH_PREFIX${NC}"

function check_file_present(){
     local_file=$1
     if [ ! -f "$local_file" ]; then
        echo -e "${RED}ERROR: $LINENO: File $local_file not found. Exiting${NC}"
        exit 1
     fi
}

# function to check all field for Bug ticket
function check_bug_ticket(){
  if [ "$1" = "null" ]; then
    echo -e "${RED}JIRA FIELD: The bug resolution field is empty. Please provide a resolution.${NC}" >> /tmp/error_fields
  fi
  if [ "$2" = "null" ]; then
    echo -e "${RED}JIRA FIELD: The Jira Resolved As field is not selected. Please select a resolution option${NC}" >>/tmp/error_fields
  fi
  if [ -f /tmp/error_fields ]; then
    cat /tmp/error_fields
    exit 1
  fi
}

# function to check field for Story ticket
function check_story_ticket(){
  if [ $1 = "null" ]; then
    echo -e "${RED}ERROR: The JIRA field 'FF Added' has not been updated. Please ensure that 'FF Added' is updated before proceeding${NC}">> /tmp/error_fields

  fi

  if [ -f /tmp/error_fields ]; then
    cat /tmp/error_fields
    exit 1
  fi
}

SHDIR=$(dirname "$0")
PROJFILE="$SHDIR/jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

COMMIT_CONTENT="\[feat]|\[fix]|\[techdebt]|\[hotfixpreqa]|feat|fix|techdebt|hotfixpreqa"
KEY=`echo "${ghprbPullTitle}" | grep -o -iE "\[(${PROJECTS})-[0-9]+][: ]" | grep -o -iE "(${PROJECTS})-[0-9]+"`
if [[ -z $KEY ]]; then
  echo -e "${RED}Cannot extract Jira key from $ghprbPullTitle${NC}"
  echo -e "${RED}Make sure that your PR Title is in format -> ${COMMIT_CONTENT}: [${PROJECTS}-<number>]: <description>${NC}"
  exit 1
fi



echo -e "${YELLOW}JIRA Key is : $KEY ${NC}"

jira_response=`curl -X GET -H "Content-Type: application/json" https://harness.atlassian.net/rest/api/2/issue/${KEY}?fields=issuetype,customfield_10687,customfield_10709,customfield_10748,customfield_10763,customfield_10785,priority --user $JIRA_USERNAME:$JIRA_PASSWORD`

errorsFound=`echo "${jira_response}" | jq ".errorMessages" | tr -d '"'`
if [[ ! $errorsFound == "null" ]]; then
  echo -e "${RED}ERROR returned fetching Jira key given in PR title ($KEY)${NC}"
  echo "$errorsFound"
  exit 1
fi

issuetype=`echo "${jira_response}" | jq ".fields.issuetype.name" | tr -d '"'`
prioritytype=`echo "${jira_response}" | jq ".fields.priority.name" | tr -d '"'`

# No longer require what changed or phase injected in fields BT-950
# Once again remove the check for what changed or phase injected BT-3453
PRIORITY_LIST=("P2","P3","P4")

if [[ $KEY == BT-* || $KEY == SPG-* ]]
then
  bug_resolution="n/a"
  ff_added="n/a"
  jira_resolved_as="n/a"
else
  bug_resolution=`echo "${jira_response}" | jq ".fields.customfield_10687" | tr -d '"'`
  ff_added=`echo "${jira_response}" | jq ".fields.customfield_10785.value" | tr -d '"'`
  jira_resolved_as=`echo "${jira_response}" | jq ".fields.customfield_10709.value" | tr -d '"'`
fi

# BT-1465 - Disallow PRs on issuetype question
if [ "${issuetype}" = "Question" ]; then
  echo -e "${RED}ERROR: Cannot commit code on Question issue type.${NC}"
  exit 1
fi

# shellcheck disable=SC2076
if [[ "${BRANCH_PREFIX}" = "release/"  && ( ${PRIORITY_LIST[*]} =~ "${prioritytype}" ) ]]
then
  echo -e "${RED}ERROR: Hotfix merge to 'release/*' is blocked, unless it's P0 or P1. Use capital letters for Jira ID in PR message.${NC}"

  # check ticket fields
  if [ "${issuetype}" = "Story" ]; then
    check_story_ticket "${ff_added}"
  elif [ "${issuetype}" = "Bug" ]; then
    check_bug_ticket "${bug_resolution}" "${jira_resolved_as}"
  fi
  exit 1
fi

echo -e "${YELLOW}ISSUETYPE is ${issuetype}${NC}"
echo -e "${YELLOW}INFO: Checking JIRA STATUS OF issueType ${issuetype}${NC}"

if [ "${issuetype}" = "Bug" ]; then
  check_bug_ticket "${bug_resolution}" "${jira_resolved_as}"
elif [ "${issuetype}" = "Story" ]; then
  check_story_ticket "${ff_added}"
fi

echo -e "${GREEN}JIRA Key is : $KEY has all the mandatory details${NC}"
