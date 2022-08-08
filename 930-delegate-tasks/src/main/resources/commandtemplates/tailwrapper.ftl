#!/usr/bin/env sh

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

# Start tail.

<#list tailPatterns as tailPattern>
export GREP_COLOR="01;32"

#Touch the file first or realpath wont work
touch "${tailPattern.filePath}"
# Get real path for the script.
TAIL_FILE_PATH=$(realpath "${tailPattern.filePath}")
#touch file again to make sure it works.
touch "$TAIL_FILE_PATH"

if [ -e "$TAIL_FILE_PATH" ]
then
printf "File exists $TAIL_FILE_PATH"
else
sleep 30
touch "$TAIL_FILE_PATH"
if [ ! -e "$TAIL_FILE_PATH" ]
then
printf "File could not be created"
exit 1
fi
fi

#now tail the file
tail -F -n0 "$TAIL_FILE_PATH" | grep --line-buffered --color=always -A10 -B10 "${tailPattern.pattern}" 2>&1 > ${executionStagingDir}/tailoutput${executionId}${tailPattern?index} &
pid${tailPattern?index}=$!
</#list>

WINGS_SCRIPT_NAME=$1
shift

# Run the script
$WINGS_SCRIPT_DIR/$WINGS_SCRIPT_NAME

bold=$(tput bold)
normal=$(tput sgr0)
boldgreen='\x1b[1;32m'
boldred='\x1b[1;31m'


echo " "
echo " "
echo -e "${r"${bold}"}Starting tail log verification${r"${normal}"}"

#Wait for tail outputs.
TAIL_TIMEOUT=1200
TAIL_COUNT=${tailPatterns?size}
while [ "$TAIL_TIMEOUT" -gt 0 -a "$TAIL_COUNT" -gt 0 ]
do
<#list tailPatterns as tailPattern>
  if [ -s ${executionStagingDir}/tailoutput${executionId}${tailPattern?index} ]
  then
    if $(kill -0 $pid${tailPattern?index} 2>/dev/null)
    then
      kill -9 $pid${tailPattern?index} || true
      TAIL_COUNT=$((TAIL_COUNT - 1))
    fi
  fi
</#list>
  sleep 1;
  TAIL_TIMEOUT=$((TAIL_TIMEOUT - 1))
done

#Kill remaining tails after timeout.
<#list tailPatterns as tailPattern>
if $(kill -0 $pid${tailPattern?index} 2>/dev/null)
then
  kill -9 $pid${tailPattern?index} || true
fi
</#list>

returnvalue=0

#Print outputs.
<#list tailPatterns as tailPattern>
echo " "
echo " "
echo "===================================================================================================="
printf "Searching file ${r"${bold}"}'$TAIL_FILE_PATH'${r"${normal}"} for pattern ${r"${boldgreen}"}'${tailPattern.pattern}'${r"${normal}"} ... "
if [ -s ${executionStagingDir}/tailoutput${executionId}${tailPattern?index} ]
then
  printf "${r"${boldgreen}"}[Found]${r"${normal}"}\n"
  echo "===================================================================================================="
  echo "Output: "
  cat ${executionStagingDir}/tailoutput${executionId}${tailPattern?index}
else
  printf "${r"${boldred}"}[Not Found]${r"${normal}"}\n"
  returnvalue=1
fi
</#list>

if [ "$returnvalue" -eq 1 ]
then
  #Summarize failures
  echo " "
  echo " "
  printf "${r"${bold}"}Unable to following patterns: ${r"${normal}"}\n"
  <#list tailPatterns as tailPattern>
  if [ ! -s ${executionStagingDir}/tailoutput${executionId}${tailPattern?index} ]
  then
    echo "File    : '$TAIL_FILE_PATH'"
    echo "Pattern : '${tailPattern.pattern}'"
  fi
</#list>
fi

echo " "
echo " "
echo "${r"${bold}"}Tail log verification finished${r"${normal}"}"


exit $returnvalue
