# Step 3: Set the Number of Pods

In **Instances**, enter the number of pods to scale up or down compared to the number of instances specified *before* the **K8s Scale** step.

The number may come from the manifest in Service or a previous step, whichever set the number of pods right before the **K8s Scale** step.

For example, in you have `replicas: 4` in a manifest, and you enter **50** **PERCENT** in **Instances**, then 2 pods are deployed in this step.

If you have an odd number of instances, such as 3 instances, and then enter 50% in **K8s Scale**, the number of instances is scaled down to 2.### Step 4: Specify Resources to Scale

In **Workload**, enter the name of the resource in the format `[namespace/]Kind/Name`, with `namespace` optional. For example: 

`my-namespace/Deployment/harness-example-deployment-canary`

You can scale Deployment, DaemonSet, or StatefulSet.

You can only enter one resource in **Workload**. To scale another resource, add another **K8s Scale** step.

In **Workload**, you can use a Harness expression for the output of another step, like the **K8s Canary** step, in **Workload**. 

For example, here is a deployed Canary step where you can copy the workload expression:

![](./static/scale-kubernetes-replicas-22.png)

The expression will look something like this:

`<+pipeline.stages.Canary.spec.execution.steps.canaryDepoyment.steps.canaryDeployment.output.canaryWorkload>`

Enter that expression in **Workload** and Harness will scale that workload.
