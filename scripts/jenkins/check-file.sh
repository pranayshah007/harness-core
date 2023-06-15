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

# Define colors
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
set -e
export TargetBranch=$(echo "${ghprbTargetBranch}")
export SourceBranch=$(echo "${ghprbSourceBranch}")

PR_Name=("SmartPRChecks-PMD" "SmartPRChecks-codebasehashcheck")
PR_TI=("TIAll-ut0" "TIAll-ut1" "TIAll-ut2" "TIAll-ut3" "TIAll-ut4" "TIAll-JavaUnitTests5" "TIAll-JavaUnitTests6" "TIAll-JavaUnitTests7" "TIAll-JavaUnitTests8" "TIAll-JavaUnitTests9")
SONAR_SCAN=("SonarQube Code Analysis" "SonarPR-SonarScan")

merge_summary=""
bazelignore_array=($(cat bazelignore))
tmp_array=($(cat sonarIgnore))
sonarignore_array=($(printf "%s\n" "${bazelignore_array[@]}" "${tmp_array[@]}" | sort -u))

BASE_SHA="$(git merge-base "$COMMIT_SHA" "$BASE_COMMIT_SHA")"
merge_summary=( $(git diff --name-only $COMMIT_SHA..$BASE_SHA) )
echo -e "${YELLOW}Merge Summary:\e[0m ${merge_summary[@]}"

compile="true"
sonar="true"
GO_FILES=False

function compile_check() {
  for file_name in "${merge_summary[@]}"; do
    for i in "${bazelignore_array[@]}"; do
      if [[ $file_name =~ ^$i  ]]; then
        printf >&2 "${GREEN}Compilation is not required for file $file_name :REGEX=$i ${NC}\n"
        compile=False
        break
      else
        compile=True
      fi
    done
    if [ $compile == True ]; then
      echo -e "${YELLOW}Compilation is required for $file_name ${NC}" >&2
      break
    fi
  done
  echo "$compile"
}

function sonar_check() {
  for file_name in "${merge_summary[@]}"; do
    for i in "${sonarignore_array[@]}"; do
      if [[ $file_name =~ ^$i  ]]; then
        printf >&2 "${GREEN}Sonar is not required for file $file_name :REGEX=$i \n"
        sonar=False
        break
      else
        sonar=True
      fi
    done
    if [ $sonar == True ]; then
      echo -e "${YELLOW}SONAR is required for $file_name ${NC}" >&2
      break
    fi
  done
  echo "$sonar"
}

function go_build() {
  for file in "${merge_summary[@]}"; do
    # Check if the file does not have the .go extension
    if [[ $file == *.go ]]; then
      echo -e "${YELLOW}GO Build is required for $file ${NC}" >&2
      GO_FILES=True
      break
    else
      echo -e "${YELLOW}GO Build is not required for $file ${NC}" >&2
      GO_FILES=False
    fi
  done
  echo "$GO_FILES"
}

function print_log() {
  printf "${YELLOW}##################################${NC}\n"
  printf "${GREEN} Compilation is not required as files/folders are added in bazelignore${NC}\n"
  printf "${GREEN} Also marking the heavy checks as NotRequired${NC}\n"

  for checks in "${PR_Name[@]}"; do
    printf "${GREEN} ${checks}\t NotRequired ${NC}\n"
  done

  for checks in "${PR_TI[@]}"; do
    printf "${GREEN} ${checks}\t NotRequired ${NC}\n"
  done

  for checks in "${SONAR_SCAN[@]}"; do
      printf "${GREEN} ${checks}\t NotRequired ${NC}\n"
  done

  echo -e "${YELLOW}Merge Summary:\e[0m ${merge_summary[@]}\n"
  printf "${YELLOW} ##################################${NC}\n\n"
  export COMPILE="false"
  echo "false" >/tmp/COMPILE
}

function CodeformatRequired() {
  Total_Files=${#merge_summary[@]}
  General_Files=0

  for file in "${merge_summary[@]}";do
    case "${file##*.}" in
        MD|md|txt)
          General_Files=`expr $General_Files + 1`
          ;;
    esac
  done

  echo ${General_Files}
  echo ${Total_Files}
  if [ $General_Files == $Total_Files ]; then
    PR_Name+=("SmartPRChecks-CodeformatCheckstyle")
    echo "false" > /tmp/codeformatcheck
  fi
}

function send_webhook() {
# function call to check if codeformat check is required or not
  CodeformatRequired

  for check in "${PR_Name[@]}"; do
    curl --silent --output /dev/null --location --request POST 'https://api.github.com/repos/harness/harness-core/statuses/'"$COMMIT_SHA"'' \
      --header 'Accept: application/vnd.github+json' \
      --header 'Authorization: Bearer '"$BOT_PWD"'' \
      --header 'Content-Type: application/json' \
      --data-raw '{
        "state": "success",
        "target_url": "https://app.harness.io/ng/#/account/VRuJ8-dqQH6QZgAtoBr66g/ci/orgs/default/projects/PRCHECKS/pipelines/SmartPRChecks/executions/'"$Execution_Id"'/pipeline",
        "description": "Skipped the check as compilation is not required!",
        "context": "'"${check}"'"
        }'
  done

  for check in "${PR_TI[@]}"; do
      curl --silent --output /dev/null --location --request POST 'https://api.github.com/repos/harness/harness-core/statuses/'"$COMMIT_SHA"'' \
        --header 'Accept: application/vnd.github+json' \
        --header 'Authorization: Bearer '"$BOT_PWD"'' \
        --header 'Content-Type: application/json' \
        --data-raw '{
          "state": "success",
          "target_url": "https://app.harness.io/ng/#/account/VRuJ8-dqQH6QZgAtoBr66g/ci/orgs/default/projects/PRCHECKS/pipelines/TIAll/executions/'"$Execution_Id"'/pipeline",
          "description": "Skipped the check as compilation is not required!",
          "context": "'"${check}"'"
          }'
    done

  for check in "${SONAR_SCAN[@]}"; do
        curl --silent --output /dev/null --location --request POST 'https://api.github.com/repos/harness/harness-core/statuses/'"$COMMIT_SHA"'' \
          --header 'Accept: application/vnd.github+json' \
          --header 'Authorization: Bearer '"$BOT_PWD"'' \
          --header 'Content-Type: application/json' \
          --data-raw '{
            "state": "success",
            "target_url": "https://app.harness.io/ng/#/account/VRuJ8-dqQH6QZgAtoBr66g/ci/orgs/default/projects/PRCHECKS/pipelines/SonarPR/executions/'"$Execution_Id"'/pipeline",
            "description": "Skipped the check as the SONAR scan is not required!",
            "context": "'"${check}"'"
            }'
    done
}

function send_sonar_webhook() {
    for check in "${SONAR_SCAN[@]}"; do
          curl --silent --output /dev/null --location --request POST 'https://api.github.com/repos/harness/harness-core/statuses/'"$COMMIT_SHA"'' \
            --header 'Accept: application/vnd.github+json' \
            --header 'Authorization: Bearer '"$BOT_PWD"'' \
            --header 'Content-Type: application/json' \
            --data-raw '{
              "state": "success",
              "target_url": "https://app.harness.io/ng/#/account/VRuJ8-dqQH6QZgAtoBr66g/ci/orgs/default/projects/PRCHECKS/pipelines/SonarPR/executions/'"$Execution_Id"'/pipeline",
              "description": "Skipped the check as compilation is not required!",
              "context": "'"${check}"'"
              }'
      done
}

if [ -z "${merge_summary}" ]; then
  printf >&2 "${GREEN} THIS IS AN EMPTY COMMIT ${NC}"
  COMPILE=False
  SONAR=False
  GO_FILES=False
else
  COMPILE=$( compile_check )
  SONAR=$( sonar_check )
  GO_FILES=$( go_build )
fi

echo COMPILE Result: "$COMPILE"
echo GO Result: "$GO_FILES"
if [[ $COMPILE == False ]]; then
  export COMPILE="false"
  echo "false" >/tmp/COMPILE
  send_webhook
  print_log
else
  export COMPILE="true"
  echo "true" >/tmp/COMPILE
fi

if [[ $SONAR == False ]]; then
  export SONAR="false"
  echo "false" >/tmp/SONAR
  send_sonar_webhook
else
  export SONAR="true"
  echo "true" >/tmp/SONAR
fi

if [[ $GO_FILES == True ]]; then
  export GO_FILES="true"
  echo "true" >/tmp/GO_FILES
else
  export GO_FILES="false"
  echo "false" >/tmp/GO_FILES
fi

#cat /tmp/COMPILE
