#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
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

export PURPOSE=idp-service
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
echo "STEP1: INFO: Checking for Not Merged Hot Fixes in Develop."

PROJFILE="jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

git log --remotes=origin/release/${PURPOSE}/* --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > release.txt
git log --remotes=origin/[d]evelop --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq > develop.txt

NOT_MERGED=`comm -23 release.txt develop.txt`

if [ ! -z "$NOT_MERGED" ]
then
    echo "ERROR: There are jira issues in idp-service release branches that are not reflected in develop."
    exit 1
fi

#Get Previous Tag and Tagging Master Branch according to type of release.
echo "INFO: Get Previous Tag and Tagging Develop Branch according to type of release."
if [[ "$EXECUTE_NEW_CODE" == "true" ]]; then
    export SHA=`git rev-parse HEAD`
    export VERSION_FILE=idp-service/build.properties

    # break down the version number into it's components
    major=`cat ${VERSION_FILE} | grep 'build.majorVersion=' | sed -e 's: *build.majorVersion=::g'`
    minor=`cat ${VERSION_FILE} | grep 'build.minorVersion=' | sed -e 's: *build.minorVersion=::g'`
    patchVersion=`cat ${VERSION_FILE} | grep 'build.patchVersion=' | sed -e 's: *build.patchVersion=::g'`

    echo "INFO: Current Tag: $TAG: major.minor.patchVersion: ${major}.${minor}.${patchVersion}"

    # check ENV paramater RELEASE_TYPE to see which number to increment
    echo "INFO: Release Type: $RELEASE_TYPE"
    case $RELEASE_TYPE in
      major)
        echo "INFO: Incrementing major version."
        newMajor=$(($major+1))
        newMinor=0
        newPatchVersion=0
        ;;
      minor)
        echo "INFO: Incrementing minor version."
        newMajor=$major
        newMinor=$(($minor + 1))
        newPatchVersion=0
        ;;
      patchVersion)
        echo "INFO: Incrementing patchVersion version."
        newMajor=$major
        newMinor=$minor
        newPatchVersion=$(($patchVersion + 1))
        ;;
      *)
        echo "ERROR: Invalid Release Type. Release type can be [major,minor,patchVersion]. Exiting..."
        exit 1
        ;;
    esac

    # echo the new version number
    export VERSION=${major}.${minor}.${patchVersion}
    export NEW_VERSION=${newMajor}.${newMinor}.${newPatchVersion}
    echo "New version: major.minor.patchVersion: VERSION"

    sed -i "s:build.majorVersion=${major}:build.majorVersion=${newMajor}:g" ${VERSION_FILE}
    sed -i "s:build.minorVersion=${minor}:build.minorVersion=${newMinor}:g" ${VERSION_FILE}
    sed -i "s:build.patchVersion=${patchVersion}:build.patchVersion=${newPatchVersion}:g" ${VERSION_FILE}

    git add ${VERSION_FILE}
    newBranch="${major}_${minor}"
    echo ${newBranch}
    git commit -m "Branching to release/${PURPOSE}/${newBranch}. New version ${NEW_VERSION}"
    git push origin develop

    echo "STEP3: INFO: Creating a release branch for ${PURPOSE}"

    git checkout ${SHA}
    git checkout -b release/${PURPOSE}/${newBranch}

    sed -i "s:build.majorVersion=${major}:build.majorVersion=${major}:g" ${VERSION_FILE}
    sed -i "s:build.minorVersion=${minor}:build.minorVersion=${minor}:g" ${VERSION_FILE}
    sed -i "s:build.patchVersion=${patchVersion}:build.patchVersion=${patchVersion}:g" ${VERSION_FILE}

    git add ${VERSION_FILE}
    git commit --allow-empty -m "Set the proper version branch release/${PURPOSE}/${newBranch}"
    git push origin release/${PURPOSE}/${newBranch}
fi
