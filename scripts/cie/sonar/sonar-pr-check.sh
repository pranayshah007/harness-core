#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#set -ex

trap 'clean_up_files' EXIT

trap 'report_error' ERR

function clean_temp_files() {
  local_files=( $1 )
  for file in ${local_files[@]}
   do
      [ -f $file ] && rm -f $file
   done
}

function report_error(){
    echo 'please re-trigger this stage after resolving the issue.'
    echo 'to trigger this specific stage comment "trigger ss" in your github PR'
    clean_temp_files "${JARS_FILE} ${MODULES_FILE} ${MODULES_TESTS_FILE} ${PR_SRCS_FILE} ${LIBS_FILE}"
    exit 1
}

function usage() {
  echo "This scripts runs sonar scan to generate reports for vulnerabilities \
  on files in a PR."
  exit 0
}

function check_cmd_status() {
  if [ $1 != 0 ]; then
      echo "ERROR: $LINENO: $2. Exiting..."; exit 1
  fi
}

function create_empty_files() {
  for file in $1
   do
      [ -f $file ] && >$file || >$file
   done
}

function clean_up_files() {
    clean_temp_files "${JARS_FILE} ${MODULES_FILE} ${MODULES_TESTS_FILE} ${PR_SRCS_FILE} ${LIBS_FILE}"
}

function get_info_from_file(){
  local_filename=$1
  cat $local_filename | sort -u | tr '\r\n' ',' | rev | cut -c2- | rev | sed 's/^,//; s/,$//'
  check_cmd_status "$?" "Unable to find file to extract info for sonar file."
}

BAZEL_ARGS="--announce_rc --keep_going --show_timestamps --jobs=auto"
BAZEL_OUTPUT_PATH="/tmp/execroot/harness_monorepo/bazel-out/k8-fastbuild/bin"
COVERAGE_ARGS="--collect_code_coverage --combined_report=lcov --coverage_report_generator=//tools/bazel/sonarqube:sonarqube_coverage_generator"
COVERAGE_REPORT_PATH='/tmp/execroot/harness_monorepo/bazel-out/_coverage/_coverage_report.dat'
JARS_ARRAY=("libmodule-hjar.jar" "libmodule.jar" "module.jar")
JARS_FILE="modules_jars.txt"
LIBS_FILE="modules_libs.txt"
MODULES_FILE="modules.txt"
MODULES_TESTS_FILE="modules_tests.txt"
PR_SRCS_FILE="pr_srcs.txt"
SONAR_CONFIG_FILE='sonar-project.properties'
STARTUP_ARGS="--output_base=/tmp"
TEST_ARGS="--discard_analysis_cache --notrack_incremental_state --nokeep_state_after_build --test_verbose_timeout_warnings --test_output=errors"

# This script is required to generate the test util bzl file in root directory.
scripts/bazel/generate_credentials.sh

while getopts ":hb:c:k:" opt; do
  case $opt in
    h) usage; exit 5 ;;
    b) BASE_SHA=$OPTARG ;;
    c) COMMIT_SHA=$OPTARG ;;
    k) SONAR_KEY=$OPTARG ;;
    *) echo "Invalid Flag"; exit 5 ;;
  esac
done

# Running a Bazel query to list down all Java targets
HARNESS_CORE_MODULES=$(bazel query "//...:*" | grep -w "module" | awk -F/ '{print $3}' | sort -u | tr '\r\n' ' ')
check_cmd_status "$?" "Failed to list harness core modules."
#echo "HARNESS_CORE_MODULES: $HARNESS_CORE_MODULES"

GIT_DIFF=$(git diff --name-only $COMMIT_SHA..$BASE_SHA)

echo "------------------------------------------------"
echo -e "GIT DIFF:\n$GIT_DIFF"
echo "------------------------------------------------"

# Filtering out only Java files for sonar coverage
for FILE in $GIT_DIFF;
  do
    extension=${FILE##*.}
    if [ -f "${FILE}" ] && [ "${extension}" = "java" ]; then
      FILES+=("${FILE}")
      echo "$(echo ${FILE} | awk -F/ '{print $1}')" >> $MODULES_FILE
    else
      echo "${FILE} not found or is not a Java File....."
    fi
  done

# Check if the file is empty, meaning there is no java file changed in the PR.
if ! [[ -f "$MODULES_FILE" ]]; then
  echo "INFO: No Java File change detected. Skipping the Scan....."
  exit 0
elif ! [[ -z $(grep -inr '400-rest' "$MODULES_FILE") ]]; then
  echo "INFO: 400-rest changes detected in the PR. Skipping the Scan....."
  exit 0
fi

PR_FILES=$(echo ${FILES[@]} | sort -u | tr ' ' ',')
check_cmd_status "$?" "Failed to get diff between commits."
echo -e "PR_FILES:\n${PR_FILES}"

PR_MODULES=$(cat $MODULES_FILE | sort -u | tr '\n' ' ')
check_cmd_status "$?" "Failed to get modules from commits."
echo -e "PR_MODULES:\n$PR_MODULES"

# Checking if PR contains more than 1 module changes
if [ "$(cat $MODULES_FILE | sort -u | wc -l)" -gt 1 ]; then
    echo "ERROR: PR is touching multiple modules, Generating Coverage and Code Smells is not possible. Exiting....."
    exit 1
fi

# Filtering out Bazel modules only and Getting the path of the sonar prop file in the module.
for module in $PR_MODULES
  do
     [ -d ${module} ] && [[ "${HARNESS_CORE_MODULES}" =~ "${module}" ]] \
     && BAZEL_COMPILE_MODULES+=("//${module}/...") \
     && SONAR_PROP_FILE_PATH=$(find ${module} -type f -iname "${SONAR_CONFIG_FILE}") \
     || echo "$module is not present in the bazel modules list."
  done

if [ -f ${SONAR_PROP_FILE_PATH} ]; then
  echo "SONAR PROP FILE: ${SONAR_PROP_FILE_PATH}"
else
  echo "SONAR PROP FILE NOT FOUND. Exiting....."
  exit 1
fi

# Running Bazel Coverage
echo "INFO: BAZEL COMMAND: bazel ${STARTUP_ARGS} test ${BAZEL_ARGS} ${COVERAGE_ARGS} ${TEST_ARGS} -- ${BAZEL_COMPILE_MODULES[@]}"
bazel ${STARTUP_ARGS} test ${BAZEL_ARGS} ${COVERAGE_ARGS} ${TEST_ARGS} -- "${BAZEL_COMPILE_MODULES[@]}" || true
check_cmd_status "$?" "Failed to run coverage."

# Splitting path till 'src' folder inside module
for file in $(echo $GIT_DIFF | tr '\r\n' ' ')
  do
     TEMP_RES=$(grep -w 'src' <<< $file | sed 's|src|:|' | awk -F: '{print $1}' | sed 's|$|src|')
     echo "$TEMP_RES" >> $PR_SRCS_FILE
  done
PR_SRCS_DIR=$(cat $PR_SRCS_FILE | sort -u)
echo -e "PR_SRCS_DIR:\n${PR_SRCS_DIR}"

# Getting all test classes in the module
for DIR in ${PR_SRCS_DIR}
  do
    find ${DIR} -type f -name "*Test.java" | tr '\r\n' ',' >> $MODULES_TESTS_FILE
  done
MODULES_TESTS=$(get_info_from_file $MODULES_TESTS_FILE)

# Getting list of generated jars after bazel build/test/coverage
for MODULE in ${PR_SRCS_DIR[@]}
  do
    TEMP_RES=$(grep -w 'src' <<< ${MODULE} | sed 's|\/src||')
    find "${BAZEL_OUTPUT_PATH}/${TEMP_RES}" -type f -name '*.jar' >> $LIBS_FILE
    echo -e "\n" >> $LIBS_FILE
    for JAR in ${JARS_ARRAY[@]}
      do
        echo "INFO: Looking for ${BAZEL_OUTPUT_PATH}/${TEMP_RES}/$JAR"
        if [ -f "${BAZEL_OUTPUT_PATH}/${TEMP_RES}/$JAR" ]; then
          echo "INFO: Found: ${BAZEL_OUTPUT_PATH}/${TEMP_RES}/$JAR"
          echo "${BAZEL_OUTPUT_PATH}/${TEMP_RES}/$JAR" >> $JARS_FILE
        fi
      done
  done
echo -e "JARS:\n$(cat ${JARS_FILE})"
JARS_BINS=$(get_info_from_file $JARS_FILE)
LIBS_BINS=$(get_info_from_file $LIBS_FILE)

# Preparing Sonar Properties file
[ -f "${SONAR_PROP_FILE_PATH}" ] \
&& echo -e "\nsonar.java.binaries=$JARS_BINS" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.java.libraries=$LIBS_BINS" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.coverageReportPaths=$COVERAGE_REPORT_PATH" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.pullrequest.key=$PR_NUMBER" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.pullrequest.branch=$PR_BRANCH" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.pullrequest.base=$BASE_BRANCH" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.pullrequest.github.repository=$REPO_NAME" >> ${SONAR_PROP_FILE_PATH} \
|| echo "ERROR: Sonar Properties File Not Found."

echo "INFO: Sonar Properties"
cat ${SONAR_PROP_FILE_PATH}

echo "INFO: Running Sonar Scan."
[ -f "${SONAR_PROP_FILE_PATH}" ] \
&& sonar-scanner -Dsonar.login=${SONAR_KEY} -Dsonar.host.url=https://sonar.harness.io \
-Dproject.settings=${SONAR_PROP_FILE_PATH}\
|| echo "ERROR: Sonar Properties File not found."
