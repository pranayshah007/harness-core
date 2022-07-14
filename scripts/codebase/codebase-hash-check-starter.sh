#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

export PR_NUMBER=$1

echo "python codebase-hash-check.py <+trigger.prNumber>"

chmod +x codebase-hash-check.py
python codebase-hash-check.py ${PR_NUMBER}

cat STATUS.txt
STATUS=$(cat STATUS.txt | grep "STATUS" | awk -F= '{print $2}' | grep "0" | sort -u)
echo "INFO: STATUS=$STATUS"

if [ -z $STATUS ]; then
    export STATUS=1
else
    export STATUS=0
fi

rm -f STATUS.txt
