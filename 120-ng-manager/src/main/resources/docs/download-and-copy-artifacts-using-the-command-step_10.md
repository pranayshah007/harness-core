# Copy an artifact or config

The deployment artifact for the stage is set in the Service Definition **Artifacts** section.

Using the **Copy** command type, you can copy the deployment artifact onto the target hosts of the deployment.

The deployment config file(s) for the stage is set in the Service Definition **Config Files** section.

![](./static/download-and-copy-artifacts-using-the-command-step-11.png)

1. In **Select file type to copy**, click **Artifact** or **Config**.

![](./static/download-and-copy-artifacts-using-the-command-step-12.png)

You simply set where you want to download the artifact in **Destination Path**.

For SSH/WinRM deployments, the path `$HOME/<+service.name>/<+env.name>` is added automatically when you select the Execution Strategy for the stage.

For example, a destination path for a stage that deploys **todolist.war** using a Service named **tutorial-service-ssh2** to an Environment named **ssh-tutorial-env** will look like this:

`$HOME/tutorial-service-ssh2/ssh-tutorial-env/todolist.war`

You can use any path on the target hosts you want. Harness will not create the path if it does not exist.

Here's an example of the results of a Copy Artifact command:

![](./static/download-and-copy-artifacts-using-the-command-step-13.png)

Here's an example of the results of a Copy Config command:

![](./static/download-and-copy-artifacts-using-the-command-step-14.png)

**Deployment Templates:** to run the Copy command on the target hosts, add the command after the **Fetch Instances** step. See [Looping Strategy and target hosts](download-and-copy-artifacts-using-the-command-step.md#looping-strategy-and-target-hosts) below.
