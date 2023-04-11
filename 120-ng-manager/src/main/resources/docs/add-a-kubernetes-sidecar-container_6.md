# Step 3: Deploy the Sidecar

Sidecars are deployed, rolled back, and have their releases versioned the same as primary containers.

If you are using **Artifacts**, when you deploy a Pipeline with a sidecar Artifact, you are prompted to select the sidecar artifact tag as well as the primary artifact tag:

![](./static/add-a-kubernetes-sidecar-container-26.png)

For examples of standard deployments, see:

* [Create a Kubernetes Rolling Deployment](../../cd-execution/kubernetes-executions/create-a-kubernetes-rolling-deployment.md)
* [Create a Kubernetes Canary Deployment](../../cd-execution/kubernetes-executions/create-a-kubernetes-canary-deployment.md)
* [Create a Kubernetes Blue Green Deployment](../../cd-execution/kubernetes-executions/create-a-kubernetes-blue-green-deployment.md)

