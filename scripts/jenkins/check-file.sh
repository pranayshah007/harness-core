#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

# Copyright 2020 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#Run the TI,FT and other bazel builds based on the commit

set -e
export TargetBranch=$(echo "${ghprbTargetBranch}")
export SourceBranch=$(echo "${ghprbSourceBranch}")

PR_Name=("SmartPRChecks-Functional_tests" "SmartPRChecks-Functional_tests1" "SmartPRChecks-PMD" "SmartPRChecks-codebasehashcheck")
PR_TI=("TIAll-ut0" "TIAll-ut1" "TIAll-ut2" "TIAll-ut3" "TIAll-ut4" "TIAll-JavaUnitTests5" "TIAll-JavaUnitTests6" "TIAll-JavaUnitTests7" "TIAll-JavaUnitTests8" "TIAll-JavaUnitTests9")

merge_summary=""
bazelignore_array=($(cat bazelignore))

BASE_SHA="$(git merge-base "$COMMIT_SHA" "$BASE_COMMIT_SHA")"
merge_summary=( $(git diff --name-only $COMMIT_SHA..$BASE_SHA) )

echo "Merge Summary:" "${merge_summary[@]}"
compile="true"

function compile_check() {
  for file_name in "${merge_summary[@]}"; do
    for i in "${bazelignore_array[@]}"; do
      if [[ $file_name =~ ^$i  ]]; then
        printf >&2 "Compilation is not required for file $file_name :REGEX=$i \n"
        compile=False
        break
      else
        compile=True
      fi
    done
    if [ $compile == True ]; then
      echo >&2 "Compilation is required for " "$file_name"
      break
    fi
  done
  echo "$compile"
}

function print_log() {
  printf "##################################\n\n\n"
  echo "Compilation is Not Required as files/folders are added in bazelignore"
  echo "Also marking the heavy checks NotRequired!"
  for checks in "${PR_Name[@]}"; do
    printf "$checks\t NotRequired \n"
  done
  for checks in "${PR_TI[@]}"; do
    printf "$checks\t NotRequired \n"
  done
  echo "Merge Summary:" "${merge_summary[@]}"
  printf "\n\n\n##################################\n\n"
  export COMPILE="false"
  echo "false" >/tmp/COMPILE
}

function send_webhook() {
  for i in "${PR_Name[@]}"; do
    curl --silent --output /dev/null --location --request POST 'https://api.github.com/repos/harness/harness-core/statuses/'"$COMMIT_SHA"'' \
      --header 'Accept: application/vnd.github+json' \
      --header 'Authorization: Bearer '"$BOT_PWD"'' \
      --header 'Content-Type: application/json' \
      --data-raw '{
"state": "success",
"target_url": "https://app.harness.io/ng/#/account/VRuJ8-dqQH6QZgAtoBr66g/ci/orgs/default/projects/PRCHECKS/pipelines/SmartPRChecks/executions/'"$Execution_Id"'/pipeline",
"description": "Skipped the check as compilation is not required!",
"context": "'"$i"'"
}'
  done
  for i in "${PR_TI[@]}"; do
      curl --silent --output /dev/null --location --request POST 'https://api.github.com/repos/harness/harness-core/statuses/'"$COMMIT_SHA"'' \
        --header 'Accept: application/vnd.github+json' \
        --header 'Authorization: Bearer '"$BOT_PWD"'' \
        --header 'Content-Type: application/json' \
        --data-raw '{
  "state": "success",
  "target_url": "https://app.harness.io/ng/#/account/VRuJ8-dqQH6QZgAtoBr66g/ci/orgs/default/projects/PRCHECKS/pipelines/TIAll/executions/'"$Execution_Id"'/pipeline",
  "description": "Skipped the check as compilation is not required!",
  "context": "'"$i"'"
  }'
    done

}

if [ -z "${merge_summary}" ]; then
  echo "THIS IS AN EMPTY COMMIT"
  COMPILE=False
  else
    COMPILE=$( compile_check )
fi

echo Overall Result: "$COMPILE"
if [[ $COMPILE == False ]]; then
  export COMPILE="false"
  echo "false" >/tmp/COMPILE
  send_webhook
  print_log
else
  export COMPILE="true"
  echo "true" >/tmp/COMPILE
fi

#cat /tmp/COMPILE
