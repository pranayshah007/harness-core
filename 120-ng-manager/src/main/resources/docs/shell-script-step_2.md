# Shell Script Step Overview

With the Shell Script step, you can execute scripts in the shell session of the stage in the following ways:

* Execute scripts on the host running a Harness Delegate. You can use Delegate Selectors to identify which Harness Delegate to use.
* Execute scripts on a remote target host in the deployment Infrastructure Definition.

When executing a script, you can also **dynamically capture** the execution output from the script, providing runtime variables based on the script execution context, and export those to another step in the same stage or another stage in the same Pipeline.

For example, you could use the Shell Script step to capture instance IDs from the deployment environment and then pass those IDs downstream to future steps or stages in the same pipeline.

If you do not publish the output variables, you can still identify which ones you want to be displayed in the deployment details and logs.The Shell Script step uses Bash. PowerShell will be coming soon. This might cause an issue if your target operating system uses a different shell. For example, bash uses printenv while KornShell (ksh) has setenv. For other shells, like ksh, create command aliases.
