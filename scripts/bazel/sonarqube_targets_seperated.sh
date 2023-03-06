#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -e

MODULES="$1"

echo "def get_sonarqube_targets_seperated():"
echo "    return {"

if [[ "$MODULES" == "401to899" ]]; then

  bazel query 'attr(tags, "sonarqube", //...:*)' | cut -c 3- | rev | cut -c 11- | rev |\
    awk ' { print "        \"//"$1":sonarqube\": \""$1"\"," } ' | sed '1,/400-rest/d' | grep "\/\/[4-8][0-9][0-9]-*"

elif [[ "$MODULES" == "only400" ]]; then

  bazel query 'attr(tags, "sonarqube", //...:*)' | cut -c 3- | rev | cut -c 11- | rev |\
    awk ' { print "        \"//"$1":sonarqube\": \""$1"\"," } ' | grep 400-rest

elif [[ "$MODULES" == "000to399" ]]; then

  bazel query 'attr(tags, "sonarqube", //...:*)' | cut -c 3- | rev | cut -c 11- | rev |\
    awk ' { print "        \"//"$1":sonarqube\": \""$1"\"," } ' | sed -n '/400-rest/q;p'

elif [[ "$MODULES" == "900to999toIndivi" ]]; then

  bazel query 'attr(tags, "sonarqube", //...:*)' | cut -c 3- | rev | cut -c 11- | rev |\
      awk ' { print "        \"//"$1":sonarqube\": \""$1"\"," } ' | sed '1,/400-rest/d' | grep "\/\/9[0-9][0-9]-*" > 900-onwards.log

  bazel query 'attr(tags, "sonarqube", //...:*)' | cut -c 3- | rev | cut -c 11- | rev |\
        awk ' { print "        \"//"$1":sonarqube\": \""$1"\"," } ' | sed '1,/400-rest/d' | grep -v "\/\/[0-9][0-9][0-9]-*" >> 900-onwards.log

  modules=$(cat 900-onwards.log)
  echo "$modules"

else
  echo "No Module Range Provided...Exiting"; exit 1
fi

echo "    }"
