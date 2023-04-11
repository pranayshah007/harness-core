# Step 5: Add a Rollout Deployment Step

1. Click **Continue**.
2. In **Execution Strategies**, select **Rolling**, and then click **Use Strategy**.

  ![](./static/azure-cd-quickstart-107.png)
  
  The **Rollout Deployment** step is added.

  ![](./static/azure-cd-quickstart-108.png)
  
  This is a standard [Kubernetes rolling update](https://kubernetes.io/docs/tutorials/kubernetes-basics/update/update-intro/). By default, Harness uses a `25% max unavailable, 25% max surge` strategy.

That's it. Now the Pipeline stage is complete and you can deploy.
