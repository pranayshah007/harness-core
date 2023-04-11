### Uninstall command flag

If you want to use the uninstall command in the **Manifest Details**, be aware of the following:

* When the deployment is successful, Harness won't execute this command.
* If the deployment fails on the very first execution, then Harness will apply the `--uninstall` flag itself. You can see this in the logs under `Wait For Steady State`.
* If you want to pass in some command flags when Harness performs the `--uninstall`, enter uninstall in **Manifest Details** and enter in the relevant command flags.

