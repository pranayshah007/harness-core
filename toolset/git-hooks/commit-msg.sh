#!/usr/bin/env bash

CHECK_MESSAGE=hook.commit-msg.check
if [ "`git config $CHECK_MESSAGE`" == "false" ]
then
    echo -e '\033[0;31m' checking message is disabled - to enable: '\033[0;37m'git config --unset $CHECK_MESSAGE '\033[0m'
else
    echo -e '\033[0;34m' checking message ... to disable: '\033[0;37m'git config --add $CHECK_MESSAGE false '\033[0m'

    # regex to validate in commit msg
    commit_regex='^\[(BT|CCE|CCM|CDC|CDNG|CDP|CE|CI|CV|CVNG|DEL|DOC|DX|ER|FFM|ONP|OPS|PIE|PL|SEC|SWAT|GTM|LWG)-[0-9]+]: |Merge branch '
    error_msg="Aborting commit. [`cat $1`] is missing a JIRA Issue and Commit Content. Example Commit Message: \"[JIRAProject-123]: Message \""

    if [ ! -z "`cat $1`" ]
    then
        if ! grep -iqE "$commit_regex" "$1"; then
            echo "$error_msg" >&2
            exit 1
        fi
    fi
fi


