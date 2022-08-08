#!/usr/bin/env sh

# set session
set -m

# Set Environment Variables.
<#list envVariables?keys as envVariable>
export ${envVariable}="${envVariables[envVariable]}"
</#list>

# Display Environment Variables.
<#list safeEnvVariables?keys as safeEnvVariable>
echo "export ${safeEnvVariable}=\"${safeEnvVariables[safeEnvVariable]}\""
</#list>

if [ "$#" -gt 1 ]
then
  key="$1"
  case $key in
    -w)
    shift # past argument
    eval WINGS_SCRIPT_WORKING_DIRECTORY="$1"
    cd "$WINGS_SCRIPT_WORKING_DIRECTORY"
    shift
    ;;
    *)
    ;;
  esac
fi

WINGS_SCRIPT_NAME="$1"
shift

$WINGS_SCRIPT_DIR/$WINGS_SCRIPT_NAME "$@"
