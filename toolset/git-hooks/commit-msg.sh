#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function check_file_present(){
     local_file=$1
     if [ ! -f "$local_file" ]; then
        echo "ERROR: $LINENO: File $local_file not found. Exiting"
        exit 1
     fi
}

SHDIR=$(dirname "$0")
PROJFILE="$SHDIR/jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)
COMMIT_CONTENT="\[feat]|\[fix]|\[techdebt]|feat|fix|techdebt"
CHECK_MESSAGE=hook.commit-msg.check
if [ "`git config $CHECK_MESSAGE`" == "false" ]
then
    echo -e '\033[0;31m' checking message is disabled - to enable: '\033[0;37m'git config --unset $CHECK_MESSAGE '\033[0m'
else
    echo -e '\033[0;34m' checking message ... to disable: '\033[0;37m'git config --add $CHECK_MESSAGE false '\033[0m'

    # regex to validate in commit msg
    commit_regex='^\('$COMMIT_CONTENT'):[('$PROJECTS')-[0-9]+]: |Merge branch '
    echo "your message should be in following format --> Text:[JiraID]: Text "
    error_msg="Aborting commit. [`cat $1`] is missing a JIRA Issue and Commit Content. Example Commit Message: \"[feat]|\[fix]|\[techdebt]|feat|fix|techdebt:[JIRAProject-123]: Message \""

    if [ ! -z "`cat $1`" ]
    then
        if ! grep -iqE "$commit_regex" "$1"; then
            echo "$error_msg" >&2
            exit 1
        fi
    fi
fi
