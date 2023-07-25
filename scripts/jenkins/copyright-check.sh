#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

FILES_WITHOUT_STMTS=()

HARNESS_INC="Copyright [0-9]{4} Harness Inc. All rights reserved."

EXCLUSION_LIST='\.remote\|\.pem\|\.tgz\|\.tpl\|\.lock\|\.helmignore\|\.mod\|\.sum\|\.log\|\.toml\|\.yml\|\.yaml\|\.properties\|\.md\|\.json\|\.config\|\.env\|\.txt\|\.info\|\.jks\|\.mod\|\.env\|\.xml\|\.jfc\|\.MD\|\.factories\|exclusion-file\|project/bazelproject\|scripts/jenkins/bazelignore\|scripts/jenkins/sonarIgnore\|resources/mockito-extensions\|\.bazelignore\|\.datacollection\|\.tf\|\.ftl\|\.gitignore\|^\.'

MERGE_SUMMARY=($(git diff --name-only $COMMIT_SHA..$BASE_SHA | grep -v ${EXCLUSION_LIST}))

echo "${MERGE_SUMMARY[@]}"

for file in ${MERGE_SUMMARY[@]}
do
    [ -f $file ] && [ -z "$(grep -E "${HARNESS_INC}" $file)" ] && FILES_WITHOUT_STMTS+=( "$file" )
done

len=${#FILES_WITHOUT_STMTS[@]}

if [ $len -eq 0 ]; then
  echo -e "\e[1;32mINFO:\e[0m All files have a copyright statement..."
else
  echo -e "\e[1;31mERROR:\e[0m Following ${len} files do not have the Copyright statements. Please update and re-trigger the execution using \"trigger copyrightcheck\" comment..."
  for file in ${FILES_WITHOUT_STMTS[@]}
  do
    echo "-> $file"
  done
  exit 1
fi






