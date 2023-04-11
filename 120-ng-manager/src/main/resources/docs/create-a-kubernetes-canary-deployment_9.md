## Canary Deployment Step

In this step, you define how many pods are deployed for a Canary test of the configuration files in your Service Definition **Manifests** section.

![](./static/create-a-kubernetes-canary-deployment-03.png)

* If you selected **Instance Count**, this is simply the number of pods.
* If you selected **Percentage**, enter a percentage of the pods defined in your Service Definition **Manifests** files to deploy.

For example, if you have `replicas: 4` in a manifest and you enter **50** for **Percentage**, then 2 pods are deployed in this step.

If you have `replicas: 3` in a manifest in Service, and you enter **50** for **Percentage**, then Harness rounds up and 2 pods are deployed in this step.

**Skip Dry Run:** By default, Harness uses the `--dry-run` flag on the `kubectl apply` command during the **Initialize** step of this command, which prints the object that would be sent to the cluster without really sending it. If the **Skip Dry Run** option is selected, Harness will not use the `--dry-run` flag.#### Canary Delete Step

Since the **Canary Deployment** step was successful, it is no longer needed. The **Canary Delete** step is used to clean up the workload deployed by the **Canary Deployment** step. See [Canary Delete Step](../../cd-technical-reference/cd-k8s-ref/kubernetes-delegate-step.md).

For step on deleting other Kubernetes resources, you can use the standard **Delete** step. See [Delete Kubernetes Resources](delete-kubernetes-resources.md).
