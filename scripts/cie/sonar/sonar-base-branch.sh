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
#COVERAGE_ARGS="--collect_code_coverage --combined_report=lcov --coverage_report_generator=//tools/bazel/sonarqube:sonarqube_coverage_generator"
COVERAGE_REPORT_PATH='/tmp/execroot/harness_monorepo/bazel-out/_coverage/_coverage_report.dat'
JARS_ARRAY=("libmodule-hjar.jar" "libmodule.jar" "module.jar")
JARS_FILE="modules_jars.txt"
LIBS_FILE="modules_libs.txt"
MODULES_FILE="modules.txt"
MODULES_TESTS_FILE="modules_tests.txt"
PR_SRCS_FILE="pr_srcs.txt"
SONAR_CONFIG_FILE='sonar-project.properties'
#STARTUP_ARGS="--output_base=/tmp"
#TEST_ARGS="--discard_analysis_cache --notrack_incremental_state --nokeep_state_after_build --test_verbose_timeout_warnings --test_output=errors"

# This script is required to generate the test util bzl file in root directory.
scripts/bazel/generate_credentials.sh

while getopts ":hb:m:k:" opt; do
  case $opt in
    h) usage; exit 5 ;;
    b) BASE_BRANCH=$OPTARG ;;
    m) MODULE_NAME=$OPTARG ;;
    k) SONAR_KEY=$OPTARG ;;
    *) echo "Invalid Flag"; exit 5 ;;
  esac
done

# Running a Bazel query to list down all Java targets
HARNESS_CORE_MODULES=$(bazel query "//...:*" | grep -w "module" | awk -F/ '{print $3}' | sort -u | tr '\r\n' ' ')
check_cmd_status "$?" "Failed to list harness core modules."
#echo "HARNESS_CORE_MODULES: $HARNESS_CORE_MODULES"

# Filtering out Bazel modules only and Getting the path of the sonar prop file in the module.
[ -d ${MODULE_NAME} ] && [[ "${HARNESS_CORE_MODULES}" =~ "${MODULE_NAME}" ]] \
&& BAZEL_COMPILE_MODULES+=("//${MODULE_NAME}/...") \
&& SONAR_PROP_FILE_PATH=$(find ${MODULE_NAME} -type f -iname "${SONAR_CONFIG_FILE}") \
|| echo "$module is not present in the bazel modules list."
echo "SONAR PROP FILE: ${SONAR_PROP_FILE_PATH}"

# Running Bazel Coverage
echo "INFO: BAZEL COMMAND: bazel ${STARTUP_ARGS} coverage ${BAZEL_ARGS} ${COVERAGE_ARGS} ${TEST_ARGS} -- ${BAZEL_COMPILE_MODULES[@]}"
bazel ${STARTUP_ARGS} coverage ${BAZEL_ARGS} ${COVERAGE_ARGS} ${TEST_ARGS} -- "${BAZEL_COMPILE_MODULES[@]}" || true
check_cmd_status "$?" "Failed to run coverage."

MODULE_SOURCE_PATH=$(grep -inr "sonar.sources" ${SONAR_PROP_FILE_PATH} | awk -F= '{print $2}' | sed 's|\/src\/main||')
for JAR in ${JARS_ARRAY[@]}
  do
    echo "INFO: Looking for $JAR in ${BAZEL_OUTPUT_PATH}/${MODULE_SOURCE_PATH}"
    JAR_FILE=${BAZEL_OUTPUT_PATH}/${MODULE_SOURCE_PATH}/${JAR}
    if [ -f "${JAR_FILE}" ]; then
      echo "INFO: Found: ${JAR_FILE}"
      echo "${JAR_FILE}" >> $JARS_FILE
    fi
    echo -e "\n" >> $JARS_FILE
  done

echo -e "JARS:\n$(cat ${JARS_FILE})"
JARS_BINS=$(get_info_from_file $JARS_FILE)

# Preparing Sonar Properties file
[ -f "${SONAR_PROP_FILE_PATH}" ] \
&& echo -e "\nsonar.java.binaries=$JARS_BINS" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.java.libraries=$JARS_BINS" >> ${SONAR_PROP_FILE_PATH} \
&& echo -e "\nsonar.coverageReportPaths=$COVERAGE_REPORT_PATH" >> ${SONAR_PROP_FILE_PATH} \
|| echo "ERROR: Sonar Properties File Not Found."

echo "INFO: Sonar Properties"
cat ${SONAR_PROP_FILE_PATH}

echo "INFO: Running Sonar Scan."
[ -f "${SONAR_PROP_FILE_PATH}" ] \
&& sonar-scanner -Dsonar.login=${SONAR_KEY} -Dsonar.host.url=https://sonar.harness.io \
-Dproject.settings=${SONAR_PROP_FILE_PATH} -Dsonar.branch.name=${BASE_BRANCH}\
|| echo "ERROR: Sonar Properties File not found."