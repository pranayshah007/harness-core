# Review: Artifacts and Manifests in Harness

If a public Docker image location is hardcoded in your Kubernetes manifest or values YAML file (for example, `image: nginx:1.14.2`) then you can simply add the manifest or values YAML to Harness and the Harness Delegate will pull the image during deployment.

Alternatively, you can also add the image to Harness as an Artifact in the **Service Definition**. This allows you to reference the image in your manifests and elsewhere using a Harness expression.

![](./static/define-kubernetes-manifests-27.png)

Your values YAML file refers to the Artifact using the Harness variable expression `<+artifact.image>`:


```yaml
image: <+artifact.image>
```
When you deploy, Harness connects to your repo and you select which image version/tag to deploy.

![](./static/define-kubernetes-manifests-28.png)

With a Harness Artifact referenced in your values YAML files, you can template your manifests, detaching them from a hardcoded location. This makes your manifests reusable and dynamic.

See [Add Container Images as Artifacts for Kubernetes Deployments](add-artifacts-for-kubernetes-deployments.md).
