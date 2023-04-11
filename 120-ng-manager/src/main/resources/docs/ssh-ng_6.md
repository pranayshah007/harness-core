# Add the Artifactory Connector

Harness includes Connectors for all the major artifact repositories. In this tutorial, we will use Artifactory.

1. In **Artifacts**, click **Add Primary Artifact**.
2. In **Specify Artifact Repository Type**, select **Artifactory** and click **Continue**. You can use another artifact repo if you like.
3. For the Artifactory Connector, select **New Artifactory Connector**.
4. In **Name**, enter a name for the connector: **artifactory-tutorial-connector** and click **Continue**.
5. In **Details**, enter the the following URL path for **Artifactory Repository URL**: **https://harness/frog.io/artifactory**. In this tutorial, we will use the artifacts stored in that repository.
6. For **Authentication**, click the down-drop arrow for **Username and Password**. Then, select **Anonymous (no credentials required)**. Click **Continue**.
   
   ![](./static/ssh-ng-170.png)

7. Click **Continue** to connect with Artifactory by using a Harness Delegate.
8. In **Delegates Setup**, select **Connect through the Harness Platform**.
9.  Click **Save and Continue**.
10. In **Connection Test**, Harness validates Artifactory Repository authentication and permissions for the repo. Click **Continue**.

![](./static/ssh-ng-171.png)
