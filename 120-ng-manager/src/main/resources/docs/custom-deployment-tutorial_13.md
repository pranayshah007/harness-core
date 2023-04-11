# Referencing the artifact in your files

Later, in the stage Execution, we will add a deployment step that will download and apply a Kubernetes manifest. The manifest is located in a [public GitHub repo](https://github.com/wings-software/harness-docs/blob/main/default-k8s-manifests/Manifests/deployment.yaml). 

In order for the manifest's Deployment object to use the artifact added to Harness, the expression `<+artifact.image>` is added to the image label (`image: <+artifact.image>`).

You must use `<+artifact.image>` somewhere in your stage Execution to reference the artifact you added to Harness. You can reference it in a Shell Script step, a spec or manifest, or any other relevant step or setting.

If you do not use `<+artifact.image>`, Harness will not attempt to download and deploy the artifact.

For non-containerized artifacts, use `<+artifact.path>`.

To learn more, go to [Built-in and Custom Harness Variables Reference](../../../platform/12_Variables-and-Expressions/harness-variables.md#artifact).
