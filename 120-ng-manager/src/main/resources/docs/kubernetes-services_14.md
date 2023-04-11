# Artifacts

You have two options when referencing the artifacts you want to deploy:

- Add an artifact source to the Harness service and reference it using the Harness expression `<+artifacts.primary.image>` in the values YAML file.
- Hardcode the artifact into the manifests or values YAML file.

<details>
<summary>Use the artifact expression</summary>

Add the image location to Harness as an artifact in the **Artifacts** section of the service.

![](./static/kubernetes-services-07.png)

This allows you to reference the image in your values YAML files using the Harness expression `<+artifacts.primary.image>`.

```yaml
...  
image: <+artifacts.primary.image>  
...
```

You cannot use Harness variables expressions in your Kubernetes object manifest files. You can only use Harness variables expressions in values YAML files, or Kustomize Patch file.

When you select the artifact repo for the artifact, like a Docker Hub repo, you specify the artifact and tag/version to use. 

You can select a specific tag/version, use a [runtime input](https://developer.harness.io/docs/platform/references/runtime-inputs/) so that you are prompted for the tag/version when you run the pipeline, or you can use an Harness variable expression to pass in the tag/version at execution.

Here's an example where a runtime input is used and you select which image version/tag to deploy.

![](./static/kubernetes-services-08.png)

With a Harness artifact, you can template your manifests, detaching them from a hardcoded location. This makes your manifests reusable and dynamic.

</details>

<details>
<summary>Hardcode the artifact</summary>

If a Docker image location is hardcoded in your Kubernetes manifest (for example, `image: nginx:1.14.2`), then you can simply add the manifest to Harness in **Manifests** and Kubernetes will pull the image during deployment.

When you hardcode the artifact in your manifests, any artifacts added to your Harness service are ignored.
</details>

