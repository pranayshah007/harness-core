#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# Color codes
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
RED='\033[1;31m'
NC='\033[0m' # No Color


if [ -z "${ghprbTargetBranch}" ]; then
  ghprbTargetBranch=develop
fi

BREAKING_COMMITS=$(git log --pretty=oneline --no-merges HEAD..origin/${ghprbTargetBranch} | grep '\[PR_FIX\]\|\[REFACTORING\]')

if [ ! -z "${BREAKING_COMMITS}" ]; then
  echo -e "${RED}ERROR: There are breaking commits that you need to merge.${NC}"
  echo -e "${BREAKING_COMMITS}"
  exit 1
fi

LAST_UNMERGED_SHA=$(git log --format=format:%H --no-merges HEAD..origin/${ghprbTargetBranch} | tail -n 1)

if [ -z "${LAST_UNMERGED_SHA}" ]; then
  exit 0
fi

echo -e "${YELLOW}INFO: Last unmerged commit:${NC}"
git log -n 1 ${LAST_UNMERGED_SHA}

UNMERGED_SINCE=$(git show -s --format=%ct ${LAST_UNMERGED_SHA})
NOW=$(date +'%s')
RECENCY_WINDOW=$((3*24*60*60))

if [[ $UNMERGED_SINCE -lt $(($NOW - $RECENCY_WINDOW)) ]]; then
  echo -e "${RED}ERROR: The branch is too old. Please merge your branch with ${ghprbTargetBranch}.${NC}"
  echo -e "${RED}Use /rebase comment in your PR for rebasing"
  exit 1
fi
