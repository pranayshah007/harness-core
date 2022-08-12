${failureStrategies}
${rollingSnippet
?replace("<+maxConcurrency>", maxConcurrency)
?replace("<+end>", end)
?replace("<+unit>", unitType)
?replace("<+setup_runtime_paths_script>", setupRuntimePathsScript)
?replace("<+process_stop_script>", processStopScript)
?replace("<+port_cleared_script>", portClearedScript)
?replace("<+process_run_script>", processRunScript)
?replace("<+port_listening_script>", portListeningScript)}