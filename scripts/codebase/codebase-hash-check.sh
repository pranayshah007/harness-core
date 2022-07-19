#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
bold=$(tput bold)
normal=$(tput sgr0)

errors=()
TEMP_DIR=`mktemp -d`
touch $TEMP_DIR/codehash-out.text

git fetch origin develop
git checkout develop
git checkout $GIT_BRANCH

COMMIT=$(git log develop.."$GIT_BRANCH" --pretty=format:"%h" | tail -1)
git checkout $COMMIT

bazel run "//001-microservice-intfc-tool:delegate" | tee $TEMP_DIR/codehash-out.text
CODEBASE_HASH_STRING=`cat $TEMP_DIR/codehash-out.text | grep "Codebase Hash:"`

HASH=${CODEBASE_HASH_STRING:14:64}
echo "New hash: " $HASH

git checkout develop
touch $TEMP_DIR/codehash-out-develop.text
bazel run "//001-microservice-intfc-tool:delegate" | tee $TEMP_DIR/codehash-out-develop.text
CODEBASE_HASH_STRING=`cat $TEMP_DIR/codehash-out-develop.text | grep "Codebase Hash:"`
EXISTING_HASH=${CODEBASE_HASH_STRING:14:64}
echo "Existing hash: " $EXISTING_HASH

if [[ $HASH != $EXISTING_HASH ]];
then
  message="$HASH is not same sas what is generated from develop branch"
  echo -e "\n${bold}260-delegate check failed.\n${normal}"
  errors+=("$message")
else
  echo "There are no changes in dependencies for module: $MODULE"
  echo -e "\n${bold}260-delegate check is successful${normal}"
fi

if [ ${#errors[@]} -eq 0 ]; then
   echo -e "\n${bold}All checks are successful.${normal}"
else
    echo "${bold}You have changed files which are being used for delegate task communication. Your change should be backward compatible."
    echo "To get approval on this PR from code-owners make sure you have put answers to questions mentioned in the doc as part of PR comment."
    echo -e "https://harness.atlassian.net/wiki/spaces/DEL/pages/21016838831/PR+Codebasehash+Check+merge+checklist\n"
    echo "If you want to generate hash locally run this script: ./scripts/codebase/codebase-hash-check.sh $@"
    echo "Please read the documentation from the link provided below or contact delegate team for backward compatibility check."
    echo -e "https://harness.atlassian.net/wiki/spaces/DEL/pages/2000781671/Backwards+compatibility+for+delegate+task+communication\n"
    DIR="$(cd "$(dirname "$0")" && pwd)"
    source $DIR/diff-with-target-branch.sh

    echo -e "${bold}\n----------------------------------------------------------------------------\n"
    echo "If the change is backward compatible then retrigger the PR check after delegate team approval"

    for i in "${!errors[@]}"
    do
        echo $(($i +1))")" ${errors[$i]}
    done
    echo -e "\n----------------------------------------------------------------------------\n"
    echo -e "\nFailure.${normal}"
    exit 1
fi
