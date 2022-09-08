${failureStrategies}
${basicSnippet
?replace("<+setup_runtime_paths_script>", setupRuntimePathsScript)
?replace("<+setup_runtime_paths_script_war>", setupRuntimePathsScriptWar)
?replace("<+process_stop_script>", processStopScript)
?replace("<+port_cleared_script>", portClearedScript)
?replace("<+process_run_script>", processRunScript)
?replace("<+port_listening_script>", portListeningScript)
?replace("<+extend_artifact_script_powershell>", extendArtifactScriptPS)
?replace("<+create_apppool_script_powershell>", createAppPoolScriptPS)
?replace("<+create_website_script_powershell>", createWebsiteScriptPS)
?replace("<+create_virtual_directory_script_powershell>", createVirtualDirectoryScriptPS)}
