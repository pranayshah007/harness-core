#!/bin/bash

#Copyright 2021 Harness Inc. All rights reserved.
#Use of this source code is governed by the PolyForm Shield 1.0.0 license
#that can be found in the licenses directory at the root of this repository, also available at
#https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

set -e

function usage() {
  echo "This scripts runs sonar scan to generate reports for vulnerabilities \
  on files in a PR."
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

JAVA_CLASSES_PATH="/tmp/execroot/harness_monorepo/bazel-out/k8-fastbuild/bin"
JAVA_SRCS="src"
JAVA_TEST_SRCS='src/test/**/*.java'
JAVA_LIBS="*.jar"
JAVA_SRC_CLASS="_javac"
PR_MODULES_JAVAC_FILE="pr_javac_list.txt"
PR_MODULES_LIB_FILE="pr_lib_list.txt"
PR_SRCS_FILE="pr_srcs.txt"
PR_TEST_INCLUSION_FILE="pr_test_inclusions.txt"
SONAR_CONFIG_FILE='sonar-project.properties'

create_empty_files "$PR_MODULES_JAVAC_FILE $PR_SRCS_FILE $PR_TEST_INCLUSION_FILE $PR_MODULES_LIB_FILE"

while getopts ":hb:c:" opt; do
  case $opt in
    h) usage; exit 5 ;;
    b) BASE_SHA=$OPTARG ;;
    c) COMMIT_SHA=$OPTARG ;;
    *) echo "Invalid Flag"; exit 5 ;;
  esac
done

HARNESS_CORE_MODULES=$(bazel query "//...:*" | grep -w "module" | awk -F/ '{print $3}' | sort -u | tr '\r\n' ' ')
check_cmd_status "$?" "Failed to list harness core modules."
#echo "HARNESS_CORE_MODULES: $HARNESS_CORE_MODULES"

GIT_DIFF="git diff --name-only $COMMIT_SHA..$BASE_SHA"

PR_FILES=$($GIT_DIFF | tr '\r\n' ',' | rev | cut -c2- | rev)
check_cmd_status "$?" "Failed to get diff between commits."
#echo "PR_FILES: $PR_FILES"

PR_MODULES=$($GIT_DIFF | awk -F/ '{print $1}' | sort -u | tr '\r\n' ' ')
check_cmd_status "$?" "Failed to get modules from commits."
#echo "PR_MODULES: $PR_MODULES"

for file in $($GIT_DIFF | tr '\r\n' ' ')
  do
    grep -w 'src' <<< $file | sed 's|src|:|' | awk -F: '{print $1}' | sed 's|$|src|' >> $PR_SRCS_FILE
  done

for module in $PR_MODULES
  do
     [ -d ${module} ] && [[ "${HARNESS_CORE_MODULES}" =~ "${module}" ]] \
     && echo "$module is present in the bazel modules list." \
     && echo "${JAVA_CLASSES_PATH}/${module}/${JAVA_SRC_CLASS}" >> $PR_MODULES_JAVAC_FILE \
     && echo "${JAVA_CLASSES_PATH}/${module}/${JAVA_LIBS}" >> $PR_MODULES_LIB_FILE \
     && echo "${module}/${JAVA_TEST_SRCS}" >> $PR_TEST_INCLUSION_FILE \
     || echo "$module is not present in the bazel modules list"
  done

if [ ! -s $PR_MODULES_JAVAC_FILE ]; then
  echo "INFO: No need to run Sonar Scan."; exit 0
fi

export SONAR_JAVAC_FILES=$(cat $PR_MODULES_JAVAC_FILE | tr '\r\n' ',' | rev | cut -c2- | rev)
export SONAR_LIBS_FILES=$(cat $PR_MODULES_LIB_FILE | tr '\r\n' ',' | rev | cut -c2- | rev)
export SONAR_SRCS=$(cat $PR_SRCS_FILE | tr '\r\n' ',' | rev | cut -c2- | rev)
export SONAR_TEST_INCLUSIONS=$(cat $PR_TEST_INCLUSION_FILE | tr '\r\n' ',' | rev | cut -c2- | rev)

echo "sonar.sources=$SONAR_SRCS" >> ${SONAR_CONFIG_FILE}
echo "sonar.tests=$SONAR_SRCS" >> ${SONAR_CONFIG_FILE}
echo "sonar.test.inclusions=$SONAR_TEST_INCLUSIONS" >> ${SONAR_CONFIG_FILE}
echo "sonar.inclusions=$PR_FILES" >> ${SONAR_CONFIG_FILE}
echo "sonar.java.binaries=$SONAR_JAVAC_FILES" >> ${SONAR_CONFIG_FILE}
echo "sonar.java.libraries=$SONAR_LIBS_FILES" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.key=$PR_NUMBER" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.branch=$PR_BRANCH" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.base=$BASE_BRANCH" >> ${SONAR_CONFIG_FILE}
echo "sonar.pullrequest.github.repository=$REPO_NAME" >> ${SONAR_CONFIG_FILE}

echo "INFO: Sonar Properties"
cat ${SONAR_CONFIG_FILE}

clean_temp_files "$PR_MODULES_JAVAC_FILE $PR_SRCS_FILE $PR_TEST_INCLUSION_FILE $PR_MODULES_LIB_FILE"
