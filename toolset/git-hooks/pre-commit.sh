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

if git rev-parse --verify HEAD >/dev/null 2>&1
then
    against=HEAD
else
    #Initial commit : diff against an empty tree object
    against=4b825dc642cb6eb9a060e54bf8d69288fbee4904
fi

CHECK_CONFLICTS=hook.pre-commit.check-conflicts
if [ "`git config $CHECK_CONFLICTS`" == "false" ]
then
    echo -e '\033[0;31m' checking left conflicts is disabled - to enable: '\033[0;37m'git config --unset $CHECK_CONFLICTS '\033[0m'
else
    echo -e '\033[0;34m' checking left conflicts  ... to disable: '\033[0;37m'git config --add $CHECK_CONFLICTS false '\033[0m'

    . $BASEDIR/toolset/git-hooks/check_conflicts.sh
fi

ANALYSIS_PROPERTY=hook.pre-commit.analysis
if [ "`git config $ANALYSIS_PROPERTY`" == "false" ]
then
    echo -e '\033[0;31m' checking analysis is disabled - to enable: '\033[0;37m'git config --unset $ANALYSIS_PROPERTY '\033[0m'
else
    echo -e '\033[0;34m' checking analysis  ... to disable: '\033[0;37m'git config --add $ANALYSIS_PROPERTY false '\033[0m'

    . $BASEDIR/toolset/git-hooks/check_analysis.sh
fi

LICENSE_PROPERTY=hook.pre-commit.license
if [ "`git config $LICENSE_PROPERTY`" == "false" ]
then
    echo -e '\033[0;31m' checking license is disabled - to enable: '\033[0;37m'git config --unset $LICENSE_PROPERTY '\033[0m'
else
    echo -e '\033[0;34m' checking license  ... to disable: '\033[0;37m'git config --add $LICENSE_PROPERTY false '\033[0m'

    LICENSE_DIR="$BASEDIR/scripts/license"
    for file in `git diff-index --cached --name-only $against`
    do
        "$LICENSE_DIR"/add_license_header.sh -l "$LICENSE_DIR/.license-header-polyform-free-trial.txt" -f "${file}"
        git diff --exit-code -- "${file}"
        if [ "$?" -ne "0" ]
        then
            git add "${file}"
        fi
    done
fi

CHECKPROTO_PROPERTY=hook.pre-commit.protocheck
if [ "`git config $CHECKPROTO_PROPERTY`" == "false" ]
then
    echo -e '\033[0;31m' checking proto is disabled - to enable: '\033[0;37m'git config --unset $CHECKPROTO_PROPERTY '\033[0m'
else
    echo -e '\033[0;34m' checking proto  ... to disable: '\033[0;37m'git config --add $CHECKPROTO_PROPERTY false '\033[0m'

    . $BASEDIR/toolset/git-hooks/checkproto.sh
fi

FORMAT_PROPERTY=hook.pre-commit.format
if [ "`git config $FORMAT_PROPERTY`" == "false" ]
then
    echo -e '\033[0;31m' formatting is disabled - to enable: '\033[0;37m'git config --unset $FORMAT_PROPERTY '\033[0m'
else
    echo -e '\033[0;34m' formatting  ... to disable: '\033[0;37m'git config --add $FORMAT_PROPERTY false '\033[0m'

    #do the formatting
    for file in `git diff-index --cached --name-only $against | grep -E '\.(proto|java)$'`
    do
        if [ -e "${file}" ]
        then
            clang-format -i "${file}"
            git diff --exit-code -- "${file}"
            if [ "$?" -ne "0" ]
            then
                git add "${file}"
            fi
        fi
    done
fi

#POM_FORMAT_PROPERTY=hook.pre-commit.format.pom
#if [ "`git config $POM_FORMAT_PROPERTY`" == "false" ]
#then
    #echo -e '\033[0;31m' formatting poms is disabled - to enable: '\033[0;37m'git config --unset $POM_FORMAT_PROPERTY '\033[0m'
#else
    #echo -e '\033[0;34m' formatting poms ... to disable: '\033[0;37m'git config --add $POM_FORMAT_PROPERTY false '\033[0m'

    #POMS=`git diff-index --cached --name-only $against | grep "pom\.xml$"`

    #if [ ! -z "$POMS" ]
    #then
        #mvn ${MAVEN_ARGS} sortpom:sort > /dev/null
        #pushd tools > /dev/null; mvn ${MAVEN_ARGS} sortpom:sort > /dev/null; popd > /dev/null

        ##do the formatting
        #for file in `find . -type f -name pom.xml`
        #do
            #if [ -e "${file}" ]
            #then
                #git diff --exit-code -- "${file}"
                #if [ "$?" -ne "0" ]
                #then
                    #git add "${file}"
                #fi
            #fi
        #done
    #fi
#fi

GRAPHQL_FORMAT_PROPERTY=hook.pre-commit.format.graphql
if [ "`git config $GRAPHQL_FORMAT_PROPERTY`" == "false" ]
then
    echo -e '\033[0;31m' formatting graphqls is disabled - to enable: '\033[0;37m'git config --unset $GRAPHQL_FORMAT_PROPERTY '\033[0m'
else
    echo -e '\033[0;34m' formatting graphqls ... to disable: '\033[0;37m'git config --add $GRAPHQL_FORMAT_PROPERTY false '\033[0m'

    #do the formatting
    for file in `git diff-index --cached --name-only $against | grep "\.graphql$"`
    do
        if [ -e "${file}" ]
        then
            prettier --write --print-width=120 "${file}"
            git diff --exit-code -- "${file}"
            if [ "$?" -ne "0" ]
            then
                git add "${file}"
            fi
        fi
    done
fi

BAZEL_FORMAT_PROPERTY=hook.pre-commit.format.bazel
if [ "`git config $BAZEL_FORMAT_PROPERTY`" == "false" ]
then
    echo -e '\033[0;31m' formatting bazel is disabled - to enable: '\033[0;37m'git config --unset $BAZEL_FORMAT_PROPERTY '\033[0m'
else
    echo -e '\033[0;34m' formatting bazel ... to disable: '\033[0;37m'git config --add $BAZEL_FORMAT_PROPERTY false '\033[0m'

    #do the formatting
    if buildifier --version &> /dev/null
    then
        for file in `git diff-index --cached --name-only $against |\
            grep -e "BUILD.bazel$" -e "WORKSPACE$" -e ".bzl$" -e "BUILD$"`
        do
            if [ -e "${file}" ]
            then
                buildifier "${file}"
                git diff --exit-code -- "${file}"
                if [ "$?" -ne "0" ]
                then
                    git add "${file}"
                fi
            fi
      done
    fi
fi
