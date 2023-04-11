## Execution Target

You can specify where to run the script **Target Host** or **On Delegate**.

In you select On Delegate, the script is executed on whichever Delegate runs the step. You can use **Delegate Selector** in **Advanced** to pick the Delegate(s) if needed.

See [Select Delegates with Selectors](/docs/platform/2_Delegates/manage-delegates/select-delegates-with-selectors.md).

If you select **Target Host**, enter the following:

* **Target Host:** enter the IP address or hostname of the remote host where you want to execute the script. The target host must be in the **Infrastructure Definition** selected when you defined the stage **Infrastructure**, and the Harness Delegate must have network access to the target host. You can also enter the variable `<+instance.name>` and the script will execute on whichever target host is used during deployment.
* **SSH Connection Attribute:** select the execution credentials to use for the shell session. For information on setting up execution credentials, see [Add SSH Keys](../../../platform/6_Security/4-add-use-ssh-secrets.md).
