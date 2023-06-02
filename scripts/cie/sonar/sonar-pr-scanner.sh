#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

trap 'report_error' ERR

function report_error(){
    echo 'please re-trigger this stage after resolving the issue.'
    echo 'to trigger this specific stage comment "trigger ss" in your github PR'
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

function clean_temp_files() {
  for file in $1
   do
      [ -f $file ] && rm -f $file
   done
}

function get_info_from_file(){
  local_filename=$1
  cat $local_filename | sort -u | tr '\r\n' ',' | rev | cut -c2- | rev
  check_cmd_status "$?" "Unable to find file to extract info for sonar file."
}

function get_javac_path(){
  local_modulename=$1
  find $local_modulename -type d -name "_javac" | sort -u | tr '\r\n' ',' | rev | cut -c2- | rev
  check_cmd_status "$?" "Unable to find the javac path."
}

JAVA_CLASSES_PATH="/tmp/execroot/harness_monorepo/bazel-out/k8-fastbuild/bin"
JAVA_SRCS="src"
JAVA_TEST_SRCS='src/test/**/*.java'
EXCLUDE_REGISTRAR='src/**/*Registrar.java'
JAVA_LIBS="**/*.jar"
JAVA_SRC_CLASS="_javac"
PR_MODULES_JAVAC_FILE="pr_javac_list.txt"
PR_MODULES_LIB_FILE="pr_lib_list.txt"
PR_SRCS_FILE="pr_srcs.txt"
PR_TEST_INCLUSION_FILE="pr_test_inclusions.txt"
PR_REGISTRAR_EXCLUSION_FILE="pr_registrar_exclusions.txt"
SONAR_CONFIG_FILE='sonar-project.properties'
BAZEL_COMPILE_MODULES=()

BAZEL_ARGS="--announce_rc --keep_going --show_timestamps --verbose_failures --remote_max_connections=1000 --remote_retries=1"

# This script is required to generate the test util bzl file in root directory.
scripts/bazel/generate_credentials.sh

create_empty_files "$PR_MODULES_JAVAC_FILE $PR_SRCS_FILE $PR_TEST_INCLUSION_FILE $PR_MODULES_LIB_FILE"

while getopts ":hb:c:k:" opt; do
  case $opt in
    h) usage; exit 5 ;;
    b) BASE_SHA=$OPTARG ;;
    c) COMMIT_SHA=$OPTARG ;;
    k) SONAR_KEY=$OPTARG ;;
    *) echo "Invalid Flag"; exit 5 ;;
  esac
done

HARNESS_CORE_MODULES=$(bazel query "//...:*" | grep -w "module" | awk -F/ '{print $3}' | sort -u | tr '\r\n' ' ')
check_cmd_status "$?" "Failed to list harness core modules."
#echo "HARNESS_CORE_MODULES: $HARNESS_CORE_MODULES"

GIT_DIFF="git diff --name-only $COMMIT_SHA..$BASE_SHA"

echo "------------------------------------------------"
echo "GIT DIFF: $GIT_DIFF"
echo "------------------------------------------------"

for FILE in `$GIT_DIFF`;
  do
    [ -f "${FILE}" ] && PR_FILES+=("${FILE}") || echo "${FILE} not found....."
  done
PR_FILES=$(echo ${PR_FILES[@]} | tr ' ' ',')
check_cmd_status "$?" "Failed to get diff between commits."
echo "PR_FILES: ${PR_FILES}"

PR_MODULES=$($GIT_DIFF | awk -F/ '{print $1}' | sort -u | grep -v '^product\|^commons' | tr '\r\n' ' ')
check_cmd_status "$?" "Failed to get modules from commits."
echo "PR_MODULES: $PR_MODULES"

for file in $($GIT_DIFF | tr '\r\n' ' ')
  do
    grep -w 'src' <<< $file | sed 's|src|:|' | awk -F: '{print $1}' | sed 's|$|src|' >> $PR_SRCS_FILE
  done

# Running Bazel Build
for module in $PR_MODULES
  do
     [ -d ${module} ] && [[ "${HARNESS_CORE_MODULES}" =~ "${module}" ]] \
     && BAZEL_COMPILE_MODULES+=("//${module}/...") \
     || echo "$module is not present in the bazel modules list"
  done

echo "INFO: BAZEL COMMAND: bazel build ${BAZEL_ARGS} -- ${BAZEL_COMPILE_MODULES[@]} -//product/... -//commons/..."
bazel build ${BAZEL_ARGS} -- "${BAZEL_COMPILE_MODULES[@]}" -//product/... -//commons/...
check_cmd_status "$?" "Failed to build harness core modules."
# Running Bazel Build

for module in $PR_MODULES
  do
     [ -d ${module} ] && [ ${module} != 'project' ] && [[ "${HARNESS_CORE_MODULES}" =~ "${module}" ]] \
     && echo "$module is present in the bazel modules list." \
     && get_javac_path ${JAVA_CLASSES_PATH}/${module} >> $PR_MODULES_JAVAC_FILE \
     && echo "${JAVA_CLASSES_PATH}/${module}/${JAVA_LIBS}" >> $PR_MODULES_LIB_FILE \
     && echo "${module}/${JAVA_TEST_SRCS}" >> $PR_TEST_INCLUSION_FILE \
     && echo "${module}/${EXCLUDE_REGISTRAR}" >> $PR_REGISTRAR_EXCLUSION_FILE \
     || echo "$module is not present in the bazel modules list"
  done

echo "----------PR_MODULES_JAVAC_FILE-----------"
cat $PR_MODULES_JAVAC_FILE
echo "---------------------"

export SONAR_JAVAC_FILES=$(get_info_from_file $PR_MODULES_JAVAC_FILE)
export SONAR_LIBS_FILES=$(get_info_from_file $PR_MODULES_LIB_FILE)
export SONAR_SRCS=$(get_info_from_file $PR_SRCS_FILE)
export SONAR_TEST_INCLUSIONS=$(get_info_from_file $PR_TEST_INCLUSION_FILE)
export SONAR_REGISTRAR_EXCLUSIONS=$(get_info_from_file $PR_REGISTRAR_EXCLUSION_FILE)

[ ! -f "${SONAR_CONFIG_FILE}" ] \
&& echo "sonar.projectKey=harness-core-sonar-pr" > ${SONAR_CONFIG_FILE} \
&& echo "sonar.log.level=DEBUG" >> ${SONAR_CONFIG_FILE}

echo "sonar.sources=$SONAR_SRCS" >> ${SONAR_CONFIG_FILE}
echo "sonar.tests=$SONAR_SRCS" >> ${SONAR_CONFIG_FILE}
echo "sonar.test.inclusions=$SONAR_TEST_INCLUSIONS" >> ${SONAR_CONFIG_FILE}
echo "sonar.inclusions=$PR_FILES" >> ${SONAR_CONFIG_FILE}
echo "sonar.exclusions=$SONAR_REGISTRAR_EXCLUSIONS" >> ${SONAR_CONFIG_FILE}
echo "sonar.java.binaries=$SONAR_JAVAC_FILES" >> ${SONAR_CONFIG_FILE}
echo "sonar.java.libraries=$SONAR_LIBS_FILES" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.key=$PR_NUMBER" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.branch=$PR_BRANCH" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.base=$BASE_BRANCH" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.github.repository=$REPO_NAME" >> ${SONAR_CONFIG_FILE}

echo "INFO: Sonar Properties"
cat ${SONAR_CONFIG_FILE}

if [ ! -s $PR_MODULES_JAVAC_FILE ]; then
  echo "INFO: No need to run Sonar Scan."; exit 0
else
  echo "INFO: Running Sonar Scan."
  sonar-scanner -Dsonar.login=${SONAR_KEY} -Dsonar.host.url=https://sonar.harness.io
fi

clean_temp_files "$PR_MODULES_JAVAC_FILE $PR_SRCS_FILE $PR_TEST_INCLUSION_FILE $PR_MODULES_LIB_FILE"
