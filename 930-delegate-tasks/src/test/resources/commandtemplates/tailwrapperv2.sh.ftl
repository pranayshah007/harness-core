
filePatterns=""
<#list tailPatterns as tailPattern>

if $(echo "${tailPattern.filePath}" | grep -q "|"); then
  echo "File path may not contain the '|' character"
  exit 1
fi

if $(echo "${tailPattern.pattern}" | grep -q "|"); then
  echo "Match pattern may not contain the '|' character"
  exit 1
fi

if [ "$filePatterns" = "" ]; then
  filePatterns="${tailPattern.filePath}|${tailPattern.pattern}"
else
  filePatterns="$filePatterns|${tailPattern.filePath}|${tailPattern.pattern}"
fi
</#list>

if ! harness_utils_start_tail_log_verification "$filePatterns"; then
  echo "Failed to start tail log verification"
  exit 1
fi

${commandString}

harness_utils_wait_for_tail_log_verification
