# Step 4: Add a Rollout Deployment Step

Now you can select the [deployment strategy](../../cd-deployments-category/deployment-concepts.md) for this stage of the Pipeline.

1. In Execution Strategies, select **Rolling**, and then click **Use Strategy**.

   ![](./static/kubernetes-cd-quickstart-91.png)

2. The **Rollout Deployment** step is added.
   
   ![](./static/kubernetes-cd-quickstart-92.png)

   This is a standard [Kubernetes rolling update](https://kubernetes.io/docs/tutorials/kubernetes-basics/update/update-intro/). By default, Harness uses a `25% max unavailable, 25% max surge` strategy.

That's it. Now the Pipeline stage is complete and you can deploy.
