# Step 2: Add a Harness GitOps Repository

GitOps Repositories store the source manifests you want to sync with destination environments.

In the Repository setup, you will select the Agent to use when synching state.

You will also provide the credentials to use when connecting to the Git repository.

We're going to use a publicly available GitHub repo and manifests located at `https://github.com/argoproj/argocd-example-apps/tree/master/guestbook`. We'll make an anonymous connection, so no GitHub credentials are required.

1. In your Harness Project, click **GitOps**, and then click **Settings**.
2. Click **Repositories**.
3. Click **New Repository**.
   
   ![](./static/harness-cd-git-ops-quickstart-08.png)

4. In **Specify Repository Type**, click **Git**.
5. Enter the following:
   1. In **Repository Name**, enter **guestbook**.
   2. In **GitOps Agent**, select the Agent you just added and click **Apply Selected**.
   For this quickstart, we'll make an anonymous connection to a GitHub repo over HTTPS.
   3.  In **Repository URL**, enter the URL: `https://github.com/argoproj/argocd-example-apps`.
6.  Click **Continue**.
7.  In **Credentials**, in **Connection Type**, select **HTTPS**.
8.  In **Authentication**, select **Anonymous**.
9.  Click **Save & Continue**. Harness validates the connection.
    The connection is verified.
    
    ![](./static/harness-cd-git-ops-quickstart-09.png)

10. If you encounter errors, check that you have the correct repo URL and selected **HTTPS**.
11. Click **Finish**. You now have a Harness GitOps Repository added.

![](./static/harness-cd-git-ops-quickstart-10.png)
