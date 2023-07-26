#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# DRONE_NETRC_USERNAME, BOT_PASSWORD should be supplied

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

export PURPOSE=ci-manager
export STATUS_ID_TO_MOVE=151

git config --global user.email "bot@harness.io"
git config --global user.name "bot-harness"

git remote set-url origin https://${DRONE_NETRC_USERNAME}:${BOT_PASSWORD}@github.com/harness/harness-core.git

git fetch --unshallow
git fetch --all

set -ex

git fetch origin refs/heads/develop; git checkout develop && git branch
check_branch_name "develop"

# Check for not merged hot fixes
echo "STEP1: INFO: Checking for Not Merged Hot Fixes in Master."

PROJFILE="jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

git log --remotes=origin/release/${PURPOSE}/* --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > release.txt
git log --remotes=origin/[d]evelop --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > develop.txt

NOT_MERGED=`comm -23 release.txt develop.txt`

if [ ! -z "$NOT_MERGED" ]
then
    echo "ERROR: There are jira issues in ci-manager release branches that are not reflected in develop."
    exit 1
fi

# Bumping version in build.properties in develop branch.
echo "STEP2: INFO: Bumping version in build.properties in develop branch."

export SHA=`git rev-parse HEAD`
export VERSION_FILE=332-ci-manager/build.properties

export VERSION=`cat ${VERSION_FILE} | grep 'build.number=' | sed -e 's: *build.number=::g'`
export VERSION=${VERSION%??}
export NEW_VERSION=$(( ${VERSION}+1 ))

sed -i "s:build.number=${VERSION}00:build.number=${NEW_VERSION}00:g" ${VERSION_FILE}

git add ${VERSION_FILE}
git commit -m "Branching to release/${PURPOSE}/${VERSION}xx. New version ${NEW_VERSION}xx"
git push origin develop
print_err "$?" "Pushing build.properties to develop branch failed"


echo "STEP3: INFO: Creating a release branch for ${PURPOSE}"

git checkout ${SHA}
git checkout -b release/${PURPOSE}/${VERSION}xx

sed -i "s:build.number=???00:build.number=${VERSION}00:g" ${VERSION_FILE}

git add ${VERSION_FILE}
git commit --allow-empty -m "Set the proper version branch release/${PURPOSE}/${VERSION}xx"
git push origin release/${PURPOSE}/${VERSION}xx

export PREVIOUS_VERSION=$(( ${VERSION}-1 ))
export PREVIOUS_RELEASE_BRANCH="release/${PURPOSE}/${PREVIOUS_VERSION}xx"
export CURRENT_RELEASE_BRANCH="release/${PURPOSE}/${VERSION}xx"

#creating the fix version
chmod +x 332-ci-manager/release/release-branch-create-cie-version.sh
332-ci-manager/release/release-branch-create-cie-version.sh

chmod +x 332-ci-manager/release/release-branch-update-jiras.sh
332-ci-manager/release/release-branch-update-jiras.sh
