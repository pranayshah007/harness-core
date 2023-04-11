## Shell Script Steps and Failures

A failed Shell Script step does not prevent a stage deployment from succeeding.

The Shell Script step succeeds or fails based on the exit value of the script. A failed command in a script does not fail the script, unless you call `set -e` at the top.
