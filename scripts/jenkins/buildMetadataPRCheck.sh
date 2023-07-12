#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e

# Color codes
RED='\033[1;31m'
YELLOW='\033[1;33m'
GREEN='\033[1;32m'
NC='\033[0m' # No Color

export BRANCH_PREFIX=$(echo ${ghprbTargetBranch} | sed 's/\(.*\/\).*/\1/')
echo -e "${YELLOW}INFO: BRANCH_PREFIX=${BRANCH_PREFIX}${NC}"

service_folders=("pipeline-service" "access-control" "platform-service" "batch-processing" "ce-nextgen" "audit-event-streaming")

# Need confirmation for below services reference path of build.properties
# "260-delegate" "315-sto-manager" "debezium-service"

# Exit if the target branch is pre_qa and build.properties file is updated
if [ "${BRANCH_PREFIX}" = "pre_qa/" ]; then
  BASE_SHA="$(git merge-base $COMMIT_SHA $BASE_COMMIT_SHA)"
  merge_summary=( $(git diff --name-only $COMMIT_SHA..$BASE_SHA) )

  for file_name in "${merge_summary[@]}"
  do
    if [[ "${file_name}" = "build.properties" ]]; then
      echo -e "${RED}FOR PRE_QA BRANCH, build.properties FILE SHOULD NOT BE UPDATED${NC}"
      exit 1
    fi
  done
else
  echo -e "${GREEN}BRANCH IS NOT EQUAL TO PRE_QA/*${NC}"
  echo -e "${GREEN}SKIPPING THE CHECK FOR PRE_QA HOTFIX${NC}"
fi

# Get path of build.properties file for dedicated services
if [[ "${BRANCH_PREFIX}" != "release/" ]]; then
  export VERSION_FILE=build.properties
else
  for i in "${service_folders[@]}"; do
    if [[ "${ghprbTargetBranch}" == "release/$i/"* ]]; then
      export VERSION_FILE=$i/build.properties
      break
    elif [[ "${ghprbTargetBranch}" == "release/ci-manager/"* ]]; then
      export VERSION_FILE=332-ci-manager/build.properties
      break
    elif [[ "${ghprbTargetBranch}" == "release/delegate/"* ]]; then
      export VERSION_FILE=260-delegate/build.properties
      break
    elif [[ "${ghprbTargetBranch}" == "release/sto-manager/"* ]]; then
      export VERSION_FILE=315-sto-manager/build.properties
      break
    else
      export VERSION_FILE=build.properties
    fi
  done
fi

export VERSION=$(cat ${VERSION_FILE} | grep 'build.number=' | sed -e 's: *build.number=::g')
echo -e "${YELLOW}INFO: VERSION=$VERSION${NC}"

export OLD_VERSION=$(( ${VERSION}-1 ))
echo -e "${YELLOW}INFO: OLD_VERSION=$OLD_VERSION${NC}"

export PATCH=$(cat ${VERSION_FILE} | grep 'build.patch=' | sed -e 's: *build.patch=::g')
echo -e "${YELLOW}INFO: PATCH=$PATCH${NC}"

git checkout origin/${ghprbTargetBranch} -b temp246_test
git checkout ${ghprbTargetBranch}

VERSION_DIFF=$(git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | { grep "+build.number=$VERSION" || true;})
echo -e "${YELLOW}INFO: \$?: VERSION_DIFF=$VERSION_DIFF${NC}"

PATCH_DIFF=$(git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | { grep  "+build.patch=$PATCH" || true;})
echo -e "${YELLOW}INFO: \$?: PATCH_DIFF=$PATCH_DIFF${NC}"

if [[ "${ghprbTargetBranch}" == "release/pipeline-service/"* ]] && [ ! $PATCH_DIFF ]; then
  echo -e "${RED}ERROR: build.patch must be incremented in build.properties.${NC}"
  exit 1
elif [ "${BRANCH_PREFIX}" = "release/" ] && [ ! $VERSION_DIFF ] && [ ! $PATCH_DIFF ]; then
  echo -e "${RED}ERROR: Either build.number or build.patch must be incremented in build.properties.${NC}"
  exit 1
elif [ "${BRANCH_PREFIX}" = "release/" ] && [ $VERSION_DIFF ]; then
  echo -e "${YELLOW}INFO: OLD VERSION INFO.${NC}"
  git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | grep "\-build.number=$OLD_VERSION"
elif [ "${BRANCH_PREFIX}" = "release/" ] && [ $PATCH_DIFF ]; then
  echo -e "${YELLOW}INFO: OLD PATCH INFO.${NC}"
  export OLD_PATCH=$(printf %03d $(( ${PATCH}-1 )) )
  git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | grep "+build.patch=$PATCH"
  git diff temp246_test..${ghprbTargetBranch} -- ${VERSION_FILE} | grep "\-build.patch=$OLD_PATCH"
fi

if [ "${ghprbTargetBranch}" = "develop" ]; then
  git show ${VERSION_FILE} | grep "build.number" || exit 0 && exit 1
  git show ${VERSION_FILE} | grep "build.patch" || exit 0 && exit 1
fi
