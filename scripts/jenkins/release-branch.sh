#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function print_err(){
    local_cmd_status=$1
    local_cmd_msg=$2
    if [ "$local_cmd_status" != 0 ]; then
        echo "ERROR: Line $LINENO: $local_cmd_msg. Exiting..."
        exit 1
    fi
}

function check_empty_output(){
    local_cmd_name=$1
    local_message=$2
    if [ -z "${local_cmd_name}" ]; then
        echo "ERROR: Line $LINENO: $local_message. Exiting..."
        exit 1
    fi
}

function check_branch_name(){
    local_branch_name=$1
    git_branch=$(git branch | grep "*" | awk '{print $2}')
    if [ ! "${local_branch_name}" = "${git_branch}" ]; then
        echo "ERROR: Line $LINENO: Expected branch $1 however found $git_branch checked out. Exiting..."
        exit 1
    else
        echo "INFO: Expected checked out branch confirmed: $1."
    fi
}

function check_file_present(){
     local_file=$1
     if [ ! -f "$local_file" ]; then
        echo "ERROR: Line $LINENO: File $local_file not found. Exiting..."
        exit 1
     fi
}

SHDIR=$(dirname "$0")
PROJFILE="$SHDIR/jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

#RELEASE TYPE is required to increment tag accordingly.
check_empty_output "$RELEASE_TYPE" "Release Type is not defined."

# Check for not merged hot fixes
echo "STEP1: INFO: Checking for Not Merged Hot Fixes in Master."
git log --remotes=origin/release/* --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > release.txt
git log --remotes=origin/[m]aster --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > master.txt

NOT_MERGED=`comm -23 release.txt master.txt`

if [ ! -z "$NOT_MERGED" ]
then
    echo "ERROR: There are jira issues in release branches that are not reflected in master."
    exit 1
fi

#Performing operations of getting SHA and TAGGING SHA on master branch.
export BRANCH=`echo "${GIT_BRANCH}" | sed -e "s/origin\///g"`
export SHA=`git rev-parse HEAD`

check_branch_name "master"

#Get Previous Tag and Tagging Master Branch according to type of release.
echo "STEP2: INFO: Get Previous Tag and Tagging Master Branch according to type of release."
if [[ "$EXECUTE_NEW_CODE" == "true" ]]; then
    #Getting Latest Tag on master branch
    TAG=$(git describe --tags --abbrev=0 --match "[0-9]*" 2> /dev/null || echo 0.0.0)

    # break down the version number into it's components
    regex="([0-9]+).([0-9]+).([0-9]+)"
    if [[ $TAG =~ $regex ]]; then
        major="${BASH_REMATCH[1]}"
        minor="${BASH_REMATCH[2]}"
        build="${BASH_REMATCH[3]}"
    fi
    echo "INFO: Current Tag: $TAG: major.minor.build: ${major}.${minor}.${build}"

    # check ENV paramater RELEASE_TYPE to see which number to increment
    echo "INFO: Release Type: $RELEASE_TYPE"
    case $RELEASE_TYPE in
      major)
        echo "INFO: Incrementing major version."
        major=$(($major+1))
        minor=0
        build=0
        ;;
      minor)
        echo "INFO: Incrementing minor version."
        minor=$(($minor + 1))
        build=0
        ;;
      build)
        echo "INFO: Incrementing build version."
        build=$(($build + 1))
        ;;
      *)
        echo "ERROR: Invalid Release Type. Release type can be [major,minor,build]. Exiting..."
        exit 1
        ;;
    esac

    # echo the new version number
    export NEW_TAG=${major}.${minor}.${build}
    echo "New version: major.minor.build: $NEW_TAG"
    git tag -a ${NEW_TAG} ${SHA} -m "Release Tag: v${NEW_TAG}"
    print_err "$?" "Tagging Failed"
    git push origin ${NEW_TAG}
    print_err "$?" "Pushing Tag to master failed"
fi

# Bumping version in build.properties in develop branch.
echo "STEP3: INFO: Bumping version in build.properties in develop branch."
git fetch origin refs/heads/develop; git checkout develop && git branch
check_branch_name "develop"

export VERSION_FILE=build.properties

export VERSION=`cat ${VERSION_FILE} | grep 'build.number=' | sed -e 's: *build.number=::g'`
DV=`cat ${VERSION_FILE} | grep 'delegate.version=' | sed -e 's: *delegate.version=::g'`

YEAR=$(date +%y)
MONTH=$(date +%m)
yy="$(echo "$DV" | cut -d'.' -f1)"
mm="$(echo "$DV" | cut -d'.' -f2)"
mv="$(echo "$DV" | cut -d'.' -f3)"

if [ "$MONTH" -gt "$mm" ] || [ "$YEAR" -gt "$yy" ]; then NEWDELEGATEVERSION="10"; else NEWDELEGATEVERSION=$((${mv}+1)); fi

export NEWDELEGATEVERSION
export VERSION=${VERSION%??}
export NEW_VERSION=$(( ${VERSION}+1 ))

sed -i "s:build.number=${VERSION}00:build.number=${NEW_VERSION}00:g" ${VERSION_FILE}
sed -i "s#${DV}#${YEAR}.${MONTH}.${NEWDELEGATEVERSION}#g" ${VERSION_FILE}

git add ${VERSION_FILE}
git commit -m "Branching to release/${PURPOSE}/${VERSION}xx. New version ${NEW_VERSION}xx"
git push origin develop
print_err "$?" "Pushing build.properties to develop branch failed"

# Update jira issues
echo "STEP4: INFO: Update jira issues"
git fetch origin refs/heads/master; git checkout master && git branch
check_branch_name "master"
if [[ "$EXECUTE_NEW_VERSION_CODE" == "true" ]]; then
  scripts/jenkins/release-branch-create-versions.sh
fi
scripts/jenkins/release-branch-update-jiras.sh
scripts/jenkins/release-branch-update-jira_status.sh
