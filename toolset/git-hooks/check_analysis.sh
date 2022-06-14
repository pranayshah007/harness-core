#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

root_folders=()
# generate a list of target folder with potential changes
for file in $(git diff --dirstat=files,0,cumulative | cut -d '%' -f 2); do
  root_folder=$(echo "$file" | cut -d "/" -f 1)
  unique=$(echo "${root_folders[@]:0}" | grep -c "$root_folder")
  if [ "$unique" == "0" ]
  then
    root_folders+=($root_folder)
  fi
done

# bazel build targets which contain changes
for folder in "${root_folders[@]}"; do
  TARGETS=$(bazel query "attr(tags, \"analysis\", //$folder/...:*)" 2> /dev/null)
  buildable_targets=$?
  if [ $buildable_targets == 0 ]
  then
    bazel ${bazelrc} build ${GCP} ${BAZEL_ARGUMENTS} ${TARGETS} 2> /dev/null
  fi
done
