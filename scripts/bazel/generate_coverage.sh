#!/usr/bin/env zsh
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#Helper Function
function help(){
  echo -e "USAGE: generate_coverage.sh <Module Name> \n\
  Example generate_coverage.sh 990-commons-test \n\
  NOTE: Provide Only Module Name without Slashes and Dots"
  exit 0
}

#Function to check if package is installed or not
#args: $1: Name of the Package
function check_package_installed() {
  LOCAL_PACKAGE_NAME=$1
  echo "Checking if $LOCAL_PACKAGE_NAME is installed or not..."
  brew list $LOCAL_PACKAGE_NAME
  if [ "$?" -eq 1 ];then
    echo "Installing $LOCAL_PACKAGE_NAME package..."
    brew install $LOCAL_PACKAGE_NAME
  fi
}

#Function to remove and create empty directory.
#args: $1: Path of the Directory
function clear_dir(){
  LOCAL_DIR_NAME=$1
  rm -rf ${LOCAL_DIR_NAME}
  mkdir -p ${LOCAL_DIR_NAME}
}

MODULE_NAME=$1
MODULE_COVERAGE_DIR="$HOME/combined_coverage_report/${MODULE_NAME}"
TEST_FOLDER_PATTERN="*/test/*"

#Main Code Starts here

if [ "$1" = '--help' ];then
  help
fi

check_package_installed lcov
clear_dir ${MODULE_COVERAGE_DIR}

echo "Bazel Command: bazel coverage -s -k --java_toolchain=@bazel_tools//tools/jdk:toolchain_java11 --collect_code_coverage --combined_report=lcov --coverage_report_generator=@bazel_tools//tools/test:coverage_report_generator //${MODULE_NAME}/..."
bazel coverage -s -k --java_toolchain=@bazel_tools//tools/jdk:toolchain_java11 \
--collect_code_coverage --combined_report=lcov \
--coverage_report_generator=@bazel_tools//tools/test:coverage_report_generator \
//${MODULE_NAME}/...

echo "Removing Test classes from coverage report"
lcov --remove `bazel info output_path`/_coverage/_coverage_report.dat -o ${MODULE_COVERAGE_DIR}/_coverage_report.dat \
${TEST_FOLDER_PATTERN}

echo "Generating HTML Coverage Report..."
genhtml ${MODULE_COVERAGE_DIR}/_coverage_report.dat -o ${MODULE_COVERAGE_DIR}
if [ "$?" -eq 0 ];then
  echo -e "Combined HTML Report Generated: ${MODULE_COVERAGE_DIR}/index.html \n \
          Run: \"open ${MODULE_COVERAGE_DIR}/index.html\" to see report in browser."
else
  echo "There is an issue in generating combined coverage report"
  exit 1
fi
