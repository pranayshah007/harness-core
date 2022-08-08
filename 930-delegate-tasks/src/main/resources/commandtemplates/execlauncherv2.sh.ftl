#!/usr/bin/env sh

<#if includeTailFunctions>

### Functions needed for expanding paths. ###

realpath() {
    canonicalize_path "$(resolve_symlinks "$1")"
}

resolve_symlinks() {
    _resolve_symlinks "$1"
}

_resolve_symlinks() {
    _assert_no_path_cycles "$@" || return

    local dir_context path
    path=$(readlink -- "$1")
    if [ $? -eq 0 ]; then
        dir_context=$(dirname -- "$1")
        _resolve_symlinks "$(_prepend_dir_context_if_necessary "$dir_context" "$path")" "$@"
    else
        printf '%s\n' "$1"
    fi
}

_prepend_dir_context_if_necessary() {
    if [ "$1" = . ]; then
        printf '%s\n' "$2"
    else
        _prepend_path_if_relative "$1" "$2"
    fi
}

_prepend_path_if_relative() {
    case "$2" in
        /* ) printf '%s\n' "$2" ;;
         * ) printf '%s\n' "$1/$2" ;;
    esac
}

_assert_no_path_cycles() {
    local target path

    target=$1
    shift

    for path in "$@"; do
        if [ "$path" = "$target" ]; then
            return 1
        fi
    done
}

canonicalize_path() {
    if [ -d "$1" ]; then
        _canonicalize_dir_path "$1"
    else
        _canonicalize_file_path "$1"
    fi
}

_canonicalize_dir_path() {
    (cd "$1" 2>/dev/null && pwd -P)
}

_canonicalize_file_path() {
    local dir file
    dir=$(dirname -- "$1")
    file=$(basename -- "$1")
    (cd "$dir" 2>/dev/null && printf '%s/%s\n' "$(pwd -P)" "$file")
}

# Optionally, you may also want to include:

### readlink emulation ###

readlink() {
    if _has_command readlink; then
        _system_readlink "$@"
    else
        _emulated_readlink "$@"
    fi
}

_has_command() {
    hash -- "$1" 2>/dev/null
}

_system_readlink() {
    command readlink "$@"
}

_emulated_readlink() {
    if [ "$1" = -- ]; then
        shift
    fi

    _gnu_stat_readlink "$@" || _bsd_stat_readlink "$@"
}

_gnu_stat_readlink() {
    local output
    output=$(stat -c %N -- "$1" 2>/dev/null) &&

    printf '%s\n' "$output" |
        sed "s/^‘[^’]*’ -> ‘\(.*\)’/\1/
             s/^'[^']*' -> '\(.*\)'/\1/"
    # FIXME: handle newlines
}

_bsd_stat_readlink() {
    stat -f %Y -- "$1" 2>/dev/null
}

_array_size() {
  if [ "$2" != "" ]; then
    delim="$2"
  else
    delim="|"
  fi

  echo $(($(echo "$1" | grep -o "$delim" | wc -l) + 1))
}

_array_push() {
  if [ "$3" != "" ]; then
    delim="$3"
  else
    delim="|"
  fi

  if [ "$1" = "" ]; then
    echo "$2"
  else
    echo "$1$delim$2"
  fi
}

_array_get() {
  if [ "$3" != "" ]; then
    delim="$3"
  else
    delim="|"
  fi

  echo "$1" | cut -d "$delim" -f $2
}

harness_utils_start_tail_log_verification() {

  nextArg="file"

  filePathArray=""
  patternArray=""

  if $(echo "$1" | grep -q "|"); then
    inputSize=$(_array_size "$1")
    for index in `seq 1 $inputSize`; do
      item=$(_array_get "$1" $index)
      if [ "$nextArg" = "file" ]; then
        filePathArray="$(_array_push "$filePathArray" "$item")"
        nextArg="pattern"
      elif [ "$nextArg" = "pattern" ]; then
        patternArray="$(_array_push "$patternArray" "$item")"
        nextArg="file"
      fi
    done
  else
    while [ $# -gt 0 ]; do
      if [ "$nextArg" = "file" ]; then
        filePathArray="$(_array_push "$filePathArray" "$1")"
        nextArg="pattern"
      elif [ $nextArg = "pattern" ]; then
        patternArray="$(_array_push "$patternArray" "$1")"
        nextArg="file"
      fi
      shift
    done
  fi

  filePathArraySize=$(_array_size "$filePathArray")
  patternArraySize=$(_array_size "$patternArray")

  if [ $filePathArraySize -ne $patternArraySize ]; then
    echo Must have the same number of files and patterns
    return 1
  fi

  arraySize=$filePathArraySize

  export GREP_COLOR="01;32"

  if [ -e ${executionStagingDir}/tailoutput-${executionId} ]; then
    seqnum=$(($(cat ${executionStagingDir}/tailoutput-${executionId}) + 1))
  else
    seqnum=1
  fi

  echo $seqnum > ${executionStagingDir}/tailoutput-${executionId}

  for index in `seq 1 $arraySize`; do
    filePath=$(_array_get "$filePathArray" $index)
    pattern=$(_array_get "$patternArray" $index)

    if $(echo "$filePath" | grep -q "\^"); then
      echo "File path may not contain the '^' character"
      exit 1
    fi

    if $(echo "$pattern" | grep -q "\^"); then
      echo "Match pattern may not contain the '^' character"
      exit 1
    fi

    #Touch the file first or realpath wont work
    touch "$filePath"
    # Get real path for the script.
    TAIL_FILE_PATH=$(realpath "$filePath")
    #touch file again to make sure it works.
    touch "$TAIL_FILE_PATH"

    if [ -e "$TAIL_FILE_PATH" ]; then
      printf "File exists $TAIL_FILE_PATH"
    else
      sleep 30
      touch "$TAIL_FILE_PATH"
      if [ ! -e "$TAIL_FILE_PATH" ]; then
        printf "File could not be created"
        return 1
      fi
    fi

    #now tail the file
    echo
    tail -F -n0 "$TAIL_FILE_PATH" | grep --line-buffered --color=always -A10 -B10 "$pattern" 2>&1 > ${executionStagingDir}/tailoutput-${executionId}-$seqnum-$index &
    echo "$seqnum^$index^$!^$filePath^$pattern" >> ${executionStagingDir}/tailoutput-${executionId}-$seqnum
  done
}

harness_utils_wait_for_tail_log_verification() {

  if [ ! -z "$1" ]; then
    if $(echo "$1" | grep -q "m"); then
      TAIL_TIMEOUT=$(($(echo "$1" | cut -d 'm' -f 1) * 60))
    elif $(echo "$1" | grep -q "s"); then
      TAIL_TIMEOUT=$(echo "$1" | cut -d 's' -f 1)
    else
      TAIL_TIMEOUT="$1"
    fi
  else
    TAIL_TIMEOUT=1200
  fi

  if [ -e ${executionStagingDir}/tailoutput-${executionId} ]; then
    maxseqnum=$(cat ${executionStagingDir}/tailoutput-${executionId})
  else
    echo Tail log verification was not started. Nothing to wait for.
    return 1
  fi

  pidLines=""
  for seqnum in `seq 1 $maxseqnum`; do
    if [ -e ${executionStagingDir}/tailoutput-${executionId}-$seqnum ]; then
      while IFS='' read -r line || [ -n "$line" ]; do pidLines="$(_array_push "$pidLines" "$line")"; done < ${executionStagingDir}/tailoutput-${executionId}-$seqnum
      rm ${executionStagingDir}/tailoutput-${executionId}-$seqnum
    fi
  done

  bold=$(tput bold)
  normal=$(tput sgr0)
  boldgreen='\x1b[1;32m'
  boldred='\x1b[1;31m'


  echo
  echo
  echo -e "${r"${bold}"}Starting tail log verification${r"${normal}"}"

  pidLinesSize=$(_array_size "$pidLines")
  #Wait for tail outputs.
  TAIL_COUNT=$pidLinesSize
  while [ "$TAIL_TIMEOUT" -gt 0 -a "$TAIL_COUNT" -gt 0 ]; do
    for index in `seq 1 $pidLinesSize`; do
      pidLine=$(_array_get "$pidLines" $index)
      seqnum=$(_array_get "$pidLine" 1 '^')
      idx=$(_array_get "$pidLine" 2 '^')
      if [ -s ${executionStagingDir}/tailoutput-${executionId}-$seqnum-$idx ]; then
        pid=$(_array_get "$pidLine" 3 '^')
        if $(kill -0 $pid 2>/dev/null); then
          kill -9 $pid || true
          TAIL_COUNT=$((TAIL_COUNT - 1))
        fi
      fi
    done
    sleep 1;
    TAIL_TIMEOUT=$((TAIL_TIMEOUT - 1))
  done

  #Kill remaining tails after timeout.
  for index in `seq 1 $pidLinesSize`; do
    pidLine=$(_array_get "$pidLines" $index)
    pid=$(_array_get "$pidLine" 3 '^')
    if $(kill -0 $pid 2>/dev/null); then
      kill -9 $pid || true
    fi
  done

  returnvalue=0

  #Print outputs.
  for index in `seq 1 $pidLinesSize`; do
    pidLine=$(_array_get "$pidLines" $index)
    seqnum=$(_array_get "$pidLine" 1 '^')
    idx=$(_array_get "$pidLine" 2 '^')
    filePath="$(_array_get "$pidLine" 4 '^')"
    pattern="$(_array_get "$pidLine" 5 '^')"
    TAIL_FILE_PATH=$(realpath "$filePath")
    echo
    echo
    echo "===================================================================================================="
    printf "Searching file ${r"${bold}"}'$TAIL_FILE_PATH'${r"${normal}"} for pattern ${r"${boldgreen}"}'$pattern'${r"${normal}"} ... "
    if [ -s ${executionStagingDir}/tailoutput-${executionId}-$seqnum-$idx ]; then
      printf "${r"${boldgreen}"}[Found]${r"${normal}"}\n"
      echo "===================================================================================================="
      echo "Output: "
      cat ${executionStagingDir}/tailoutput-${executionId}-$seqnum-$idx
    else
      printf "${r"${boldred}"}[Not Found]${r"${normal}"}\n"
      returnvalue=1
    fi
  done

  if [ "$returnvalue" -eq 1 ]; then
    #Summarize failures
    echo
    echo
    printf "${r"${bold}"}Unable to following patterns: ${r"${normal}"}\n"
    for index in `seq 1 $pidLinesSize`; do
      pidLine=$(_array_get "$pidLines" $index)
      seq=$(_array_get "$pidLine" 1 '^')
      idx=$(_array_get "$pidLine" 2 '^')
      filePath="$(_array_get "$pidLine" 4 '^')"
      pattern="$(_array_get "$pidLine" 5 '^')"
      TAIL_FILE_PATH=$(realpath "$filePath")
      if [ ! -s ${executionStagingDir}/tailoutput-${executionId}-$seqnum-$idx ]; then
        echo "File    : '$TAIL_FILE_PATH'"
        echo "Pattern : '$pattern'"
      fi
    done
  fi

  echo
  echo
  echo "${r"${bold}"}Tail log verification finished${r"${normal}"}"

  return $returnvalue
}

</#if>

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

eval WINGS_SCRIPT_WORKING_DIRECTORY="${scriptWorkingDirectory}"
if [ -n "$WINGS_SCRIPT_WORKING_DIRECTORY" ]; then
    cd "$WINGS_SCRIPT_WORKING_DIRECTORY"
fi
