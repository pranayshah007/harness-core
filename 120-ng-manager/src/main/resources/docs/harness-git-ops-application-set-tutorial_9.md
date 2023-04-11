# Create the Harness GitOps Application

Now that we have the Agent, Clusters, and Repo, we can create the GitOps Application.

1. In **GitOps**, click **Applications**, and then click **New Application**.
2. Enter the following settings and then click **Continue**.
	1. **Application Name:** enter **git-generator-files-discovery**.
	2. **GitOps Agent:** select the Agent you added earlier.
	3. **Service:** create a new Service named **git-generator-files-discovery**.
	4. **Environment:** create a new Environment named **git-generator-files-discovery**. 
	5. Select **Pre-Production**.
   
   ![](./static/harness-git-ops-application-set-tutorial-38.png)

3. Leave the default **Sync Policy** settings, and click **Continue**.
4. In **Source**, enter the following settings and then click **Continue**.
	1. **Repository URL:** select the GitOps Repository you added earlier.
	2. **Target Revision:** select **master**.
	3. **Path:** enter **examples/git-generator-files-discovery** and click **+** to add it.
   
   ![](./static/harness-git-ops-application-set-tutorial-39.png)

5. In **Destination**, enter the following settings and then click **Finish**.
	1. **Cluster:** select the Agent cluster **appset-example**.
	2. Namespace: enter **default**.

The GitOps Application is added. Now you can sync it.
