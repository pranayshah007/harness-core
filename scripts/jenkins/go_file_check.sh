#!/bin/bash
# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -xe

BASE_SHA="$(git merge-base $COMMIT_SHA $BASE_COMMIT_SHA)"
go_merge_summary=( $(git diff --name-only $COMMIT_SHA..$BASE_SHA) )
export GO_COMPILE="false"

for file_name in "${go_merge_summary[@]}"
do
	if [[ ${file_name: -3} == .go  ]];then
		export GO_COMPILE="true"
	fi
done

echo $GO_COMPILE
