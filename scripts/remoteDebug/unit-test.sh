#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

source ./remote-debug.sh

function checkInputs()
        {
            msg=$1; shift
            expected=$1; shift
            actual=$1; shift
            echo "$msg: "
            if [ "$expected" != "$actual" ]; then
                echo "FAILED: EXPECTED=$expected ACTUAL=$actual"
            else
                echo PASSED
            fi
        }

#####Test input variables
read_inputs pipeline-service .
checkInputs "Reading number of inputs" 2 $?


#####Test Context setting
check_context
if [ $? -eq 0 ]; then
  echo Valid Cluster!
  else
    echo Invalid Cluster
fi

#######Test Config file#######

if [ $(awk 'END { print NR }' ./config.yaml) -eq 8 ] && [ $(sort ./config.yaml | uniq -c | wc -l) -eq 8 ] ; then
  echo "Valid number of paramaters!"
  else
    echo "Invalid number of paramaters!"
fi
