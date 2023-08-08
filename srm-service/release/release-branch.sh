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
        echo "Unable to update branch version. Check git credentials and retry."
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

echo "INFO: Step 0: Setting values for env variables PURPOSE and STATUS_ID_TO_MOVE."
export PURPOSE=srm
export STATUS_ID_TO_MOVE=151
echo "INFO: Step 1: Setting git config."
git config --global user.email "bot@harness.io"
git config --global user.name "bot-harness"
echo "INFO: Step 2: Setting git remote url."
git remote set-url origin https://${DRONE_NETRC_USERNAME}:${BOT_PASSWORD}@github.com/harness/harness-core.git
echo "INFO: Step 3: Fetching git repo."
git fetch --unshallow
echo "INFO: Step 4: Fetching all git branches."
git fetch --all
echo "INFO: Step 5: Setting shell condition - exit on error TRUE."
set -e
echo "INFO: Step 6: Setting shell condition - debug TRUE."
set -x
echo "INFO: Step 7: Fetching BASE_BRANCH"
git fetch origin refs/heads/${BASE_BRANCH}; git checkout ${BASE_BRANCH} && git branch
echo "INFO: Step 8: Checking $BASE_BRANCH and current branch are the same"
check_branch_name "${BASE_BRANCH}"

echo "INFO: Step 9.1: Checking for Not Merged SRM Hot Fixes in BASE_BRANCH."
export CURRENT_BRANCH=[${BASE_BRANCH:0:1}]${BASE_BRANCH:1}
git log --remotes=origin/release/${PURPOSE}/* --pretty=oneline --abbrev-commit | grep -iE "\[SRM-[0-9]+]:" -o | sort | uniq > release_srm.txt
git log --remotes=origin/$CURRENT_BRANCH --pretty=oneline --abbrev-commit | grep -iE "\[SRM-[0-9]+]:" -o | sort | uniq > base_srm.txt

NOT_MERGED=$(comm -23 release_srm.txt base_srm.txt)

if [ ! -z "$NOT_MERGED" ]
then
    echo "ERROR: There are SRM jira issues in srm-service release branches that are not reflected in ${BASE_BRANCH}."
    exit 1
fi

echo "INFO: Step 9.2: Checking for Not Merged OIP Hot Fixes in BASE_BRANCH."
export CURRENT_BRANCH=[${BASE_BRANCH:0:1}]${BASE_BRANCH:1}
git log --remotes=origin/release/${PURPOSE}/* --pretty=oneline --abbrev-commit | grep -iE "\[OIP-[0-9]+]:" -o | sort | uniq > release_oip.txt
git log --remotes=origin/$CURRENT_BRANCH --pretty=oneline --abbrev-commit | grep -iE "\[OIP-[0-9]+]:" -o | sort | uniq > base_oip.txt

NOT_MERGED=$(comm -23 release_oip.txt base_oip.txt)

if [ ! -z "$NOT_MERGED" ]
then
    echo "ERROR: There are OIP jira issues in srm-service release branches that are not reflected in ${BASE_BRANCH}."
    exit 1
fi

echo "INFO: Step 10: Bumping version in build.properties in ${BASE_BRANCH} branch."
export SHA=$(git rev-parse HEAD)
export VERSION_FILE=srm-service/build.properties
export VERSION=$(cat ${VERSION_FILE} | grep 'build.number=' | sed -e 's: *build.number=::g')
export VERSION=${VERSION%??}
export NEW_VERSION=$(( ${VERSION}+1 ))
sed -i "s:build.number=${VERSION}00:build.number=${NEW_VERSION}00:g" ${VERSION_FILE}
git add ${VERSION_FILE}
git commit -m "Branching to release/${PURPOSE}/${VERSION}xx. New version ${NEW_VERSION}xx"
git push origin ${BASE_BRANCH}
print_err "$?" "Pushing build.properties to ${BASE_BRANCH} branch failed"

echo "INFO: Step 11: Creating a release branch for ${PURPOSE}."
git checkout ${SHA}
git checkout -b release/${PURPOSE}/${VERSION}xx
sed -i "s:build.number=???00:build.number=${VERSION}00:g" ${VERSION_FILE}
git add ${VERSION_FILE}
git commit --allow-empty -m "Set the proper version branch release/${PURPOSE}/${VERSION}xx"
git push origin release/${PURPOSE}/${VERSION}xx

echo "INFO: Step 12: Creating the srm fix version."
chmod +x srm-service/release/release-branch-create-srm-versions.sh
srm-service/release/release-branch-create-srm-versions.sh
echo "INFO: Step 13: Updating srm jiras."
chmod +x srm-service/release/release-branch-update-srm-jiras.sh
srm-service/release/release-branch-update-srm-jiras.sh

echo "INFO: Step 14: Creating the oip fix version."
chmod +x srm-service/release/release-branch-create-oip-versions.sh
srm-service/release/release-branch-create-oip-versions.sh
echo "INFO: Step 15: Updating oip jiras."
chmod +x srm-service/release/release-branch-update-oip-jiras.sh
srm-service/release/release-branch-update-oip-jiras.sh