#!/usr/bin/env bash
# Copyright 2020 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

pushd `dirname $0` > /dev/null && BASEDIR=$(pwd -L) && popd > /dev/null

echo "This script will install hooks that run scripts that could be updated without notice\n"

while true; do
    read -p "Do you wish to install these hooks, reply (yY/nN)? " yn
    case $yn in
        [Yy]* ) break;;
        [Nn]* ) exit;;
        * ) echo "Please answer yes or no.";;
    esac
done

cd $BASEDIR
find . -type f -not -name "*.sh" -exec cp "{}" ../../.git/hooks \;
echo "GitLeaks installed successfully!"
