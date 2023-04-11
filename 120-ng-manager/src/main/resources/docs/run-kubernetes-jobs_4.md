# Step 3: Add the Job to the Execution using the Apply Step

The Apply step can be used in any Kubernetes deployment strategy.

In the stage **Execution**, click **Add Step** wherever you want to deploy you Job.

In the **Apply Step**, in **File Path**, enter the path to the Job manifest relative to the path entered in the Service **Manifests** section.

For example, the path in **Manifests** is `default-k8s-manifests/Manifests/Files/templates` and the Job manifest is in the subfolder of `templates` named `jobs/job.yaml`.

In the **Apply Step**, in **File Path**, enter `jobs/job.yaml`.

![](./static/run-kubernetes-jobs-15.png)
