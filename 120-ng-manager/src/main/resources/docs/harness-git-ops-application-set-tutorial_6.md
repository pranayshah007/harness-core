## Dev and Prod Application Clusters

We'll add Harness GitOps Clusters for the two target clusters where we want to add our application.

1. Create a new Harness GitOps Cluster for your dev cluster.
2. Name the cluster **engineering-dev**.
3. In **GitOps Agent**, select the GitOps Agent you added earlier.
   
   ![](./static/harness-git-ops-application-set-tutorial-30.png)

4. In **Details**, select **Specify Kubernetes Cluster URL and credentials**.
5. In **Master URL**, enter the Endpoint URL for the target cluster (you can use `kubectl cluster-info` or your cloud console). Ensure that you use the `https://` scheme.Here's an example:
   
   ![](./static/harness-git-ops-application-set-tutorial-31.png)

6. In **Authentication**, use the authentication method you prefer. In this tutorial, we use the `default` namespace `service-account` token.
7. Click **Save and Continue**. The GitOps Cluster is verified.
8. Click **Finish**.
9.  Repeat the process for the **Prod** cluster.
	1. Name the cluster **engineering-prod**.
	2. Use the same Agent.
	3. For **Master URL**, ensure that you use the `https://` scheme.
	4. Use whatever authentication method you want.

When you're done, you will have three Harness GitOps Clusters: 1 for the GitOps Agent and two for the target clusters.

![](./static/harness-git-ops-application-set-tutorial-32.png)

You might see a Warning status. This status simply indicates that nothing has been deployed to the cluster yet.
