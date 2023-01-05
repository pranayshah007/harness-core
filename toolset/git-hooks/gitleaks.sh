#!/bin/bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#Helper script to be used as a pre-commit hook.

echo "This hook checks for any secrets getting pushed as part of commit. If you feel that scan is false positive. \
Then add the exclusion in .gitleaksignore file. For more info visit: https://github.com/zricethezav/gitleaks"

GIT_LEAKS=$(git config --bool hook.pre-commit.gitleaks)

if [ ${GIT_LEAKS} == 'true'  ]; then
    echo "INFO: Scanning Commits information for any GIT LEAKS"
    gitleaks protect -v --staged
    STATUS=$?
    if [ $STATUS != 0  ]; then
        echo "WARNING: GIT LEAKS has detected sensitive information in your changes."
        exit $STATUS
    fi
fi
