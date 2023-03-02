#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set +e

export BRANCH_PREFIX=`echo ${ghprbTargetBranch} | sed 's/\(........\).*/\1/g'`
echo "INFO: BRANCH_PREFIX=$BRANCH_PREFIX"

function check_file_present(){
     local_file=$1
     if [ ! -f "$local_file" ]; then
        echo "ERROR: $LINENO: File $local_file not found. Exiting"
        exit 1
     fi
}

# function to check all field for Bug ticket
function check_bug_ticket(){
  if [ -z "$1" ]; then
    echo "ERROR: JIRA FIELD: bug resolution is empty" >> /tmp/error_fields
  fi
  if [ "$2" = "null" ]; then
    echo "ERROR: JIRA FIELD: Jira Resolved As is not selected" >>/tmp/error_fields
  fi
  if [ "$3" = "null" ]; then
    echo "ERROR: JIRA FIELD: Phase Defect Injected is not selected" >>/tmp/error_fields
  fi
  if [ "$4" = "null" ]; then
    echo "ERROR: JIRA FIELD: What Changed ? is not updated" >> /tmp/error_fields
  fi

  if [ -f /tmp/error_fields ]; then
    cat /tmp/error_fields
    exit 1
  fi
}

# function to check field for Story ticket
function check_story_ticket(){
  if [ $1 = "null" ]; then
    echo "ERROR: JIRA FIELD: FF Added is not updated, Please update FF added to proceed" >> /tmp/error_fields
  fi
  if [ $2 = "null" ]; then
    echo "ERROR: JIRA FIELD: What Changed ? is not updated" >>/tmp/error_fields
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

# Check commit message if there's a single commit
#if [ $(git rev-list --count $ghprbActualCommit ^origin/develop)  -eq 1 ]; then
#    ghprbPullTitle=$(git log -1 --format="%s" $ghprbActualCommit)
#fi
KEY=`echo "${ghprbPullTitle}" | grep -o -iE "\[(${PROJECTS})-[0-9]+]:" | grep -o -iE "(${PROJECTS})-[0-9]+"`

echo "JIRA Key is : $KEY "

jira_response=`curl -X GET -H "Content-Type: application/json" https://harness.atlassian.net/rest/api/2/issue/${KEY}?fields=issuetype,customfield_10687,customfield_10709,customfield_10748,customfield_10763,customfield_10785,priority --user $JIRA_USERNAME:$JIRA_PASSWORD`
issuetype=`echo "${jira_response}" | jq ".fields.issuetype.name" | tr -d '"'`
prioritytype=`echo "${jira_response}" | jq ".fields.priority.name" | tr -d '"'`

# No longer require what changed or phase injected in fields
# BT-950
what_changed="n/a"
phase_injected="n/a"
## End Change for BT-950
PRIORITY_LIST=("P2","P3","P4")

if [[ $KEY == BT-* || $KEY == SPG-* ]]
then
  bug_resolution="n/a"
  what_changed="n/a"
  ff_added="n/a"
  jira_resolved_as="n/a"
  phase_injected="n/a"
else
  bug_resolution=`echo "${jira_response}" | jq ".fields.customfield_10687" | tr -d '"'`
  what_changed=`echo "${jira_response}" | jq ".fields.customfield_10763" | tr -d '"'`
  ff_added=`echo "${jira_response}" | jq ".fields.customfield_10785.value" | tr -d '"'`
  jira_resolved_as=`echo "${jira_response}" | jq ".fields.customfield_10709" | tr -d '"'`
  phase_injected=`echo "${jira_response}" | jq ".fields.customfield_10748" | tr -d '"'`
fi

# BT-1465 - Disallow PRs on issuetype question
if [ $issuetype = "Question" ]; then
  echo "ERROR: Cannot commit code on Question issue type."
  exit 1
fi

if [[ "${BRANCH_PREFIX}" = "release/"  && ( ${PRIORITY_LIST[*]} =~ "${prioritytype}" ) ]]
then
  echo "ERROR: Hotfix merge to target branch: release/* is blocked unless it is P0 or P1."

  # check ticket fields
  if [ $issuetype = "Story" ]; then
    check_story_ticket $ff_added $what_changed
  elif [ $issuetype = "Bug" ]; then
    check_bug_ticket $bug_resolution $jira_resolved_as $phase_injected $what_changed
  exit 1
fi

echo "issueType is ${issuetype}"
echo "INFO: Checking JIRA STATUS OF issueType ${issuetype}"

if [ $issuetype = "Bug" ]; then
  check_bug_ticket $bug_resolution $jira_resolved_as $phase_injected $what_changed
elif [ $issuetype = "Story" ]; then
  check_story_ticket $ff_added $what_changed
fi

echo "JIRA Key is : $KEY is having all the mandatory details"
