#!/bin/bash

# Copyright 2020 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

>merge_summary.txt
>committed_files.txt

export TargetBranch=`echo ${ghprbTargetBranch}`
export SourceBranch=`echo ${ghprbSourceBranch}`

REGEX1='^.*.sh$'
REGEX2='^.*.md$'

#git diff $TargetBranch origin/$TargetBranch --name-only
#git diff $TargetBranch origin/$TargetBranch --name-only >merge_summary.txt

git diff HEAD@{0} HEAD@{1} --name-only >merge_summary.txt

cat merge_summary.txt

while read i; do echo "${i##*/}"; done < merge_summary.txt >committed_files.txt
while read s; do echo "${s%%/*}"; done < merge_summary.txt >committed_folders.txt

sort committed_folders.txt | uniq > committed_folders_1.txt

if [[ $(grep -v -f bazelignore committed_folders_1.txt) ]] ; then
#  echo "Compilation is Required"
  compile=true
fi

if [[ $(grep -v -f bazelignore committed_files.txt) ]]  ; then
#  echo "Compilation is Required"
  compile=true
fi

while read i; do
  if [[  $i =~ $REGEX1 ]]
  then
#      echo "Found Shell script"
      compile=true
  elif [[  $i =~ $REGEX2 ]]
  then
#      echo "Found Readme File"
      compile=false
#      echo $compile
  fi
done < committed_files.txt

echo $compile

if [[ "$compile" = true ]]
then
  echo "Doing compilation"
else
  echo "Compilation is Not Required"
fi