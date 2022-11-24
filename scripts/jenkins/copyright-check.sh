#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

merge_summary=()
merge_summary+=( $(git diff HEAD@{0} HEAD@{1} --name-only) )

echo "Merge Summary: "${merge_summary[@]}
error_data=()

copyright_text="Copyright "$current_year" Harness Inc. All rights reserved."
echo "'"$copyright_text"'"

for file in ${merge_summary[@]}
do
    if [ -z "$(grep  "$copyright_text" $file)" ];
    then
      error_data+=( "$file" )
    else
      echo "checked... " $file
    fi
done

echo ${error_data[@]}
len=${#error_data[@]}

if [ $len -eq 0 ];
then
  echo "All file have up to date copyrights... "
else
  echo "please update the copyright issue in following files and re-trigger the execution... "
  for i in ${error_data[@]}
  do
    echo $i
  done
  exit 1
fi
