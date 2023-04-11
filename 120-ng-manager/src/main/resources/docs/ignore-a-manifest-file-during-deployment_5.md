# Step 1: Ignore a Manifest

You add your manifest files to the stage's **Service** section in **Manifests**. You can add manifests individually or by simply adding their parent folder:

![](./static/ignore-a-manifest-file-during-deployment-18.png)

To ignore a resource file that is in the directory in the Service **Manifests** section, you add the comment `# harness.io/skip-file-for-deploy` to the **top** of the file.

For example, here is a ConfigMap file using the comment:

![](./static/ignore-a-manifest-file-during-deployment-19.png)

Now, when this Pipeline is run, this ConfigMap resource will not be applied.

The comment `# harness.io/skip-file-for-deploy` must be at the **top** of the file. If it is on the second line it will not work and the resource will be deployed as part of the main Workflow rollout.
