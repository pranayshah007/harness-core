# Download an artifact

The deployment artifact for the stage is set in the Service of the stage.

Using the **Download** command type, you can download the deployment artifact onto the target hosts of the deployment.

![](./static/download-and-copy-artifacts-using-the-command-step-09.png)

You simply set where you want to download the artifact in **Destination Path**.

For SSH/WinRM deployments, the path `$HOME/<+service.name>/<+env.name>` is added automatically when you select the Execution Strategy for the stage.

For example, a destination path for a stage that deploys **todolist.war** using a Service named **tutorial-service-ssh2** to an Environment named **ssh-tutorial-env** will look like this:

`$HOME/tutorial-service-ssh2/ssh-tutorial-env/todolist.war`

You can use any path on the target hosts you want. Harness will not create the path if it does not exist.

Here's an example of the results of a Download command:

![](./static/download-and-copy-artifacts-using-the-command-step-10.png)

**Deployment Templates:** to run the Download command on the target hosts, add the command after the **Fetch Instances** step. See [Looping Strategy and target hosts](download-and-copy-artifacts-using-the-command-step.md#looping-strategy-and-target-hosts) below.
