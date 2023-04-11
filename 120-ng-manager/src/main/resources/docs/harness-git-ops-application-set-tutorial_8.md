# Add the GitOps Repository

Let's add a Harness GitOps Repository for your ApplicationSet repo. Later, you'll select this GitOps Repository when you set up the Harness GitOps Application.

1. In your Harness Project, click **GitOps**, and then click **Settings**.
2. Click **Repositories**.
3. Click **New Repository**.
   
   ![](./static/harness-git-ops-application-set-tutorial-35.png)

4. In **Specify Repository Type**, click **Git**.
5. Enter the following and click **Continue**.
   * **Repository Name:** **applicationset\_examples**.
   * **GitOps Agent:** select the Agent you added earlier.
   * **Git Repository URL:** the URL to your repo. You can simply copy and paste the HTTPS URL from GitHub.
   
   ![](./static/harness-git-ops-application-set-tutorial-37.png)

   1. In **Credentials**, either user your GitHub credentials or, if your repo is public, select Anonymous in Authentication.
   2. Click **Save & Continue**.
6. Once the connection is verified, click **Finish**.

Now you can create the Harness GitOps Application using the Harness GitOps Agent, Clusters, and Repositories you have set up.
