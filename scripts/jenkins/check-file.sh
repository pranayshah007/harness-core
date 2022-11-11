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

export TargetBranch=`echo ${ghprbTargetBranch}`
export SourceBranch=`echo ${ghprbSourceBranch}`

REGEX1='^.*.sh$'
REGEX2='^.*.md$'
REGEX3='^.*.txt$'

merge_summary=( $(git diff HEAD@{0} HEAD@{1} --name-only) )

echo "Merge Summary:"${merge_summary[@]}

committed_folders=()
committed_files=()

for i in "${merge_summary[@]}"
do
  if [[ "$i" == *\/* ]]; then
   committed_folders+=( "${i%%/*}" )
  else
    committed_files+=( "${i##*/}" )
    fi
done

committed_folders=($(for i in "${committed_folders[@]}"; do echo "${i}"; done | sort -u))
committed_files=($(for i in "${committed_files[@]}"; do echo "${i}"; done | sort -u))

echo "List of files committed: ${committed_files[@]}"
echo "List of folders committed: ${committed_folders[@]}"

bazelignore_array=( $(cat scripts/jenkins/bazelignore) )

compile1=false
compile2=false

for e1 in "${committed_folders[@]}"
    do
            if [[ ! " ${bazelignore_array[*]} " =~ $e1 ]]
            then
                compile1=true
                break
            else
              compile1=false
            fi
    done
echo "Folder compile $compile1"

for e1 in "${committed_files[@]}"
    do
            if [[ ! " ${bazelignore_array[*]} " =~ $e1 ]] && [[ ! $e1 =~ $REGEX1 ]] && [[ ! $e1 =~ $REGEX2 ]] && [[ ! $e1 =~ $REGEX3 ]]
            then
                compile2=true
                break
            else
              compile2=false
            fi
    done
echo "Files compile $compile2"

if [[ "$compile1" = "$compile2" ]] && [[  $compile1 == "false" ]]
then
  echo "Compilation is Not Required"
  export COMPILE="false"
  echo "false" >/tmp/COMPILE
else
    echo "Doing compilation"
    export COMPILE="true"
    echo "true" >/tmp/COMPILE

fi
echo "%%%%%% $COMPILE %%%%%%"
