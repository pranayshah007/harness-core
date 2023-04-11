## Add the Artifactory Connector

Harness includes Connectors for all the major artifact repositories. In this tutorial, we will use a **todolist.zip** file, available in a public Harness Artifactory repo.

1. In **Artifacts**, click **Add Primary Artifact**.
2. In **Specify Artifact Repository Type**, select **Artifactory** and click **Continue**.
3. For the Artifactory Connector, select **New Artifactory Connector**.
4. In **Name**, enter a name for the connector: **artifactory-connector** and click **Continue**.
5. In **Details**, enter the following URL path for **Artifactory Repository URL** or click the tooltip and copy the URL: **https://harness.jfrog.io/artifactory**. In this tutorial, we will use the artifacts stored in that repository.
6. For **Authentication**, click the down-drop arrow for **Username and Password**. Then, select **Anonymous (no credentials required)**. Click **Continue**.
   	
	![](./static/win-rm-tutorial-129.png)

1. Click **Continue** to connect with Artifactory by using a Harness Delegate.
7. In **Delegates Setup**, retain the default selection: **Use any available delegate**.
8. Click **Save and Continue**.
9.  In **Connection Test**, Harness validates the Artifactory Repository authentication and permissions for the repo. Click **Continue**. If the test fails, that means the Delegate can't connect to **https://harness.jfrog.io/artifactory/**. Make sure that the EC2 instance hosting the Delegate can make outbound connections to **https://harness.jfrog.io/artifactory**/.
