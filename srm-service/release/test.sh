#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# DRONE_NETRC_USERNAME, BOT_PASSWORD , BASE_BRANCH should be supplied

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

echo "Starting $BASE_BRANCH"

export PURPOSE=srm

echo "PURPOSE $PURPOSE"

export STATUS_ID_TO_MOVE=151

echo "STATUS_ID_TO_MOVE $STATUS_ID_TO_MOVE"

git config --global user.email "bot@harness.io"
git config --global user.name "bot-harness"

git config -l

echo "git config"

git remote set-url origin https://${DRONE_NETRC_USERNAME}:${BOT_PASSWORD}@github.com/harness/harness-core.git

echo "remote set-url"

git fetch --unshallow

echo "fetch --unshallow"
git fetch --all
echo "fetch --all"

set -ex
echo "set -ex"
git fetch origin refs/heads/$BASE_BRANCH; git checkout $BASE_BRANCH && git branch
echo "git fetch origin refs"
check_branch_name "$BASE_BRANCH"
echo "check_branch_name"

# Check for not merged hot fixes
echo "STEP1: INFO: Checking for Not Merged Hot Fixes in Master."

PROJFILE="jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

git log --remotes=origin/release/${PURPOSE}/* --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > release.txt
git log --remotes=origin/$BASE_BRANCH --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > develop.txt

NOT_MERGED=`comm -23 release.txt develop.txt`

if [ ! -z "$NOT_MERGED" ]
then
    echo "ERROR: There are jira issues in srm-service release branches that are not reflected in $BASE_BRANCH."
    exit 1
fi

# Bumping version in build.properties in develop branch.
echo "STEP2: INFO: Bumping version in build.properties in $BASE_BRANCH branch."

export SHA=`git rev-parse HEAD`
export VERSION_FILE=srm-service/build.properties

export VERSION=`cat ${VERSION_FILE} | grep 'build.number=' | sed -e 's: *build.number=::g'`
export VERSION=${VERSION%??}
export NEW_VERSION=$(( ${VERSION}+1 ))

sed -i "s:build.number=${VERSION}00:build.number=${NEW_VERSION}00:g" ${VERSION_FILE}

#TODO: Uncomment
#git add ${VERSION_FILE}
#git commit -m "Branching to release/${PURPOSE}/${VERSION}xx. New version ${NEW_VERSION}xx"
#git push origin $BASE_BRANCH
#print_err "$?" "Pushing build.properties to $BASE_BRANCH branch failed"


echo "STEP3: INFO: Creating a release branch for ${PURPOSE}"

echo "git checkout ${SHA}"
echo "git checkout -b release/${PURPOSE}/${VERSION}xx"

sed -i "s:build.number=???00:build.number=${VERSION}00:g" ${VERSION_FILE}

echo "git add ${VERSION_FILE}"
echo "git commit --allow-empty -m \"Set the proper version branch release/${PURPOSE}/${VERSION}xx\""
echo "git push origin release/${PURPOSE}/${VERSION}xx"

#creating the fix version
#TODO: Uncomment
#chmod +x srm-service/release/release-branch-create-srm-versions.sh
#srm-service/release/release-branch-create-srm-versions.sh
#
#chmod +x srm-service/release/release-branch-update-jiras.sh
#srm-service/release/release-branch-update-jiras.sh