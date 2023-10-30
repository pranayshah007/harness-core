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

# $1 - semver string
# $2 - level {release,minor,major} - release by default
function incr_semver() {
    IFS='.' read -ra ver <<< "$1"
    [[ "${#ver[@]}" -ne 3 ]] && echo "Invalid semver string" && return 1
    [[ "$#" -eq 1 ]] && level='release' || level=$2

    release=${ver[2]}
    minor=${ver[1]}
    major=${ver[0]}

    case $level in
        release)
            release=$((release+1))
        ;;
        minor)
            release=0
            minor=$((minor+1))
        ;;
        major)
            release=0
            minor=0
            major=$((major+1))
        ;;
        *)
            echo "Invalid level passed"
            return 2
    esac
    echo "$major.$minor.$release"
}

export PURPOSE=sto-manager
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
    echo "ERROR: There are jira issues in sto manager release branches that are not reflected in develop."
    exit 1
fi

# Bumping version in build.properties in develop branch.
echo "STEP2: INFO: Bumping version in build.properties in develop branch."

export VERSION_FILE=315-sto-manager/build.properties
export CHART_FILE=315-sto-manager/chart/Chart.yaml
export VALUES_FILE=315-sto-manager/chart/values.yaml


export VERSION=`cat ${VERSION_FILE} | grep 'build.number=' | sed -e 's: *build.number=::g'`
export VERSION=${VERSION%??}
export NEW_VERSION=$(( ${VERSION}+1 ))
export HELM_VERSION=`cat ${CHART_FILE} | grep '^version: ' | sed  -e 's:version\: ::g' | sed -e 's/"$//'`
export NEW_HELM_VERSION=$(incr_semver $HELM_VERSION minor)

# Update Chart.yaml & values.yaml for helm chart
sed -i "s:tag\:.*-000\":tag\: \"${VERSION}00-000\":g" ${VALUES_FILE}
sed -i "s:^appVersion.*:appVersion\: \"0.0.${VERSION}00\":g" ${CHART_FILE}
sed -i "s:^version.*:version\: ${NEW_HELM_VERSION}:g" ${CHART_FILE}

git add ${CHART_FILE}
git add ${VALUES_FILE}
git add ${VERSION_FILE}
git commit -m "Creating new helm chart version ${NEW_HELM_VERSION} with app version: ${VERSION}xx"
git push origin develop
print_err "$?" "Pushing helm chart updates for ${PURPOSE} to develop branch failed"

echo "STEP3: INFO: Updating build properties for ${PURPOSE}"
export SHA=`git rev-parse HEAD`
sed -i "s:build.number=${VERSION}00:build.number=${NEW_VERSION}00:g" ${VERSION_FILE}

git add ${VERSION_FILE}
git commit -m "Branching to release/${PURPOSE}/${VERSION}xx. New version ${NEW_VERSION}xx"
git push origin develop
print_err "$?" "Pushing build.properties to develop branch failed"

echo "STEP4: INFO: Creating a release branch for ${PURPOSE}"

git checkout ${SHA}
git checkout -b release/${PURPOSE}/${VERSION}xx
git push origin release/${PURPOSE}/${VERSION}xx
