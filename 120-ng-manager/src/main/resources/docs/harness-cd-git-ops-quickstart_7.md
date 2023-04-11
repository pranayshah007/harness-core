# Step 4: Add a Harness GitOps Application

A GitOps Application syncs a source manifest with a target cluster using a GitOps Agent.

In the Application setup, you will select the Agent, Repository, and Cluster to use when synching state.

1. In your Harness Project, click **GitOps**, and then click **New Application**.
   
   ![](./static/harness-cd-git-ops-quickstart-13.png)

2. In **Application Name**, enter **example**.
3. In **GitOps Agent**, select the Agent you added earlier.
4. In **Service**, click **New Service**, and name the Service **guestbook**.
5. In **Environment**, click **New Environment**, name the Environment **quickstart**, and select **Pre-Production**.
   
   ![](./static/harness-cd-git-ops-quickstart-14.png)

6. Click **Continue**.
7. In **Sync Policy**, you can define the following:
   + Sync Options to define how the Application syncs state.
   + Prune Policy for garbage collection of orphaned resources.
   + The Source manifest to use (Kubernetes, Helm chart, Kustomization, etc).
   + The Destination cluster and namespace.
   
   For this quickstart, we'll simply select a manual sync policy and no other options. You can change any of these settings by editing your Application or whenever you sync.
8. In **Sync Policy**, click **Manual**, and then click **Continue**.
9.  In **Source**, you specify the source repo to use.
10. In **Repository Type**, click **Git**.
11. Click in **Repository URL** and select the URL you entered in your Harness GitOps Repository: `https://github.com/argoproj/argocd-example-apps`.
12. In **Revision Type**, select **Branch**.
13. In **Revision**, select **master**.
14. Wait a moment for **Path** to populate. Harness will pull the paths from the repo.
15. In **Path**, select **helm-guestbook**. This is the location of this app in the repo: `https://github.com/argoproj/argocd-example-apps/tree/master/helm-guestbook`.
16. In **Helm**, in **Values Files**, select **values.yaml**.
17. Scroll down to see **Parameters**.
  All of the parameters from values.yaml are displayed and can be edited. This lets you modify values.yaml parameters in your Harness GitOps Application.
  Do not change the parameters for this quickstart.
1.  When you're done, **Source** will look like this:
   
   ![](./static/harness-cd-git-ops-quickstart-15.png)

2.  Click **Continue**.
3.  In **Destination**, click in **Cluster URL** and select the Cluster you added earlier.
   
   You can see its name and master URL. Since a Harness GitOps Cluster contains the authentication settings needed to access the cluster, your Harness GitOps Application can select any Cluster.
4.  In **Namespace**, enter **default**.
   
   ![](./static/harness-cd-git-ops-quickstart-16.png)

5. Click **Finish**.
6.  The new Application is created. At first, it will be **UNKNOWN**.
   
    ![](./static/harness-cd-git-ops-quickstart-17.png)

Now we can manually sync the Application.
