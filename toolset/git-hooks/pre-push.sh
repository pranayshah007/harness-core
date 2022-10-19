#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#
#This pre - commit hook checks if any versions of clang - format
#are installed, and if so, uses the installed version to format
#the staged changes.

pushd `dirname $0` > /dev/null && cd ../.. && BASEDIR=$(pwd -L) && popd > /dev/null

CHECK_CONFLICTS=hook.pre-push.check-conflicts
if [ "`git config $CHECK_CONFLICTS`" == "false" ]
then
    echo -e '\033[0;31m' checking left conflicts is disabled - to enable: '\033[0;37m'git config --unset $CHECK_CONFLICTS '\033[0m'
else
    echo -e '\033[0;34m' checking left conflicts  ... to disable: '\033[0;37m'git config --add $CHECK_CONFLICTS false '\033[0m'

    . $BASEDIR/toolset/git-hooks/check_conflicts.sh
fi

ANALYSIS_PROPERTY=hook.pre-push.analysis
if [ "`git config $ANALYSIS_PROPERTY`" == "false" ]
then
    echo -e '\033[0;31m' checking analysis is disabled - to enable: '\033[0;37m'git config --unset $ANALYSIS_PROPERTY '\033[0m'
else
    echo -e '\033[0;34m' checking analysis  ... to disable: '\033[0;37m'git config --add $ANALYSIS_PROPERTY false '\033[0m'

    . $BASEDIR/toolset/git-hooks/check_analysis.sh
fi

CHECK_BEHIND_COMMITS=hook.pre-push.behindcommits
BEHIND_COMMITS=`git config $CHECK_BEHIND_COMMITS 2>/dev/null`
if [ "$BEHIND_COMMITS" == "" ]
then
    BEHIND_COMMITS=-1
fi

if [ $BEHIND_COMMITS == -1 ]
then
    echo -e '\033[0;31m' checking behind commits is disabled - to enable: '\033[0;37m'git config --add $CHECK_BEHIND_COMMITS 3 \# or any other number \>= 0 '\033[0m'
else
    echo -e '\033[0;34m' checking behind commits  ... to disable: '\033[0;37m'git config --unset $CHECK_BEHIND_COMMITS '\033[0m'
    CURRENT_BRANCH=`git rev-parse --abbrev-ref HEAD`
    TARGET_BRANCH=`echo $CURRENT_BRANCH | sed -e "s/^\([^@]*\)$/\1@master/" | sed -e "s/^.*@//"`

    git fetch origin ${TARGET_BRANCH}:${TARGET_BRANCH}

    BEHIND=`git rev-list --left-right --count ${TARGET_BRANCH}...${CURRENT_BRANCH} | awk '{ print $1}'`

    if [ $BEHIND -gt 3 ]
    then
        echo "You are $BEHIND commits behind ${TARGET_BRANCH}. Please merge before you push."
        exit 1
    fi
fi

CHECK_PR_FIX=hook.pre-push.prfix
if [ "`git config $CHECK_PR_FIX`" == "false" ]
then
    echo -e '\033[0;31m' checking for PR fix in target branch is disabled - to enable: '\033[0;37m'git config --unset $CHECK_PR_FIX '\033[0m'
else
    echo -e '\033[0;34m' checking for PR fix target branch  ... to disable: '\033[0;37m'git config --add $CHECK_PR_FIX false '\033[0m'

    CURRENT_BRANCH=`git rev-parse --abbrev-ref HEAD`
    TARGET_BRANCH=`echo $CURRENT_BRANCH | sed -e "s/^\([^@]*\)$/\1@master/" | sed -e "s/^.*@//"`

    git fetch origin ${TARGET_BRANCH}:${TARGET_BRANCH}

    FIXES=`git rev-list --left-right --pretty ${CURRENT_BRANCH}..${TARGET_BRANCH} | grep "\[PR_FIX\]"`

    if [ ! -z "${FIXES}" ]
    then
        echo "There are unmerged PR fixes in ${TARGET_BRANCH}."
        echo ${FIXES}
        exit 1
    fi
fi
