# Step 3: Stage Deployment Step

The **Stage Deployment** step is added automatically when you apply the Blue Green strategy.

Click the **Stage Deployment** step. The step simply includes a name, timeout, and Skip Dry Run options.

**Skip Dry Run:** By default, Harness uses the `--dry-run` flag on the `kubectl apply` command during the **Initialize** step of this command, which prints the object that would be sent to the cluster without really sending it. If the **Skip Dry Run** option is selected, Harness will not use the `--dry-run` flag.The first time you deploy, the **Stage Deployment** step creates two Kubernetes services, a new pod set, and deploys your app to the pod set.

When you look at the **Stage Deployment** step in Harness **Deployments**, you will see the following log sections.
