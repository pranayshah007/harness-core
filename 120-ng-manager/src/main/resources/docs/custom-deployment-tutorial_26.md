## Command step in Deployment Template deployments

A Command step can run scripts, download artifacts, or copy artifacts and config files.

Since a Deployment Template can be used on any host type, the Command step can only be run on the Delegate. You must use the **Run on Delegate** option in the step.

Consequently, there is no reason to use a Looping Strategy when using the Command step with Deployment Templates.

For more information, go to [Use the Command step to download, copy, or run scripts](../../cd-execution/cd-general-steps/download-and-copy-artifacts-using-the-command-step.md).
